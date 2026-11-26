-- =============================================================================
-- HardwareStock / Hardware_new Supabase Security, Schema & RLS Migration Script
-- =============================================================================
-- Run this script in your Supabase SQL Editor (Project -> SQL Editor -> New query).
-- It creates any missing sync tables (`store_profile`, `gst_bills`, `color_tint_logs`),
-- enables strict Row-Level Security (RLS) on every application table with ZERO `anon`
-- policies, enforces store membership & admin role protections, makes `audit_logs`
-- append-only, provisions default users on signup, and installs `delete_user_account()`.
-- =============================================================================

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. Ensure Required Tables Exist
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.app_users (
    email text PRIMARY KEY,
    is_admin boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.store_profile (
    id text PRIMARY KEY,
    profile_json text NOT NULL DEFAULT '{}',
    updated_at bigint NOT NULL DEFAULT 0,
    updated_by text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.gst_bills (
    id text PRIMARY KEY,
    invoice_no text NOT NULL DEFAULT '',
    invoice_date text NOT NULL DEFAULT '',
    customer_name text NOT NULL DEFAULT '',
    customer_phone text NOT NULL DEFAULT '',
    customer_gstin text NOT NULL DEFAULT '',
    customer_address text NOT NULL DEFAULT '',
    store_gstin text NOT NULL DEFAULT '',
    is_inter_state boolean NOT NULL DEFAULT false,
    items_json text NOT NULL DEFAULT '[]',
    taxable_total double precision NOT NULL DEFAULT 0.0,
    total_tax_amount double precision NOT NULL DEFAULT 0.0,
    grand_total double precision NOT NULL DEFAULT 0.0,
    transporter_id text NOT NULL DEFAULT '',
    vehicle_no text NOT NULL DEFAULT '',
    quotation_id text,
    quotation_no text,
    pdf_path text,
    status text NOT NULL DEFAULT 'Generated',
    created_at text NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS public.color_tint_logs (
    tint_record_id text PRIMARY KEY,
    product_name text NOT NULL DEFAULT '',
    shade_name text NOT NULL DEFAULT '',
    shade_code text NOT NULL DEFAULT '',
    base_code text NOT NULL DEFAULT '',
    can_factor text NOT NULL DEFAULT '',
    liters double precision NOT NULL DEFAULT 0.0,
    no_of_cans integer NOT NULL DEFAULT 1,
    tint_date text NOT NULL DEFAULT '',
    tint_time text NOT NULL DEFAULT '',
    tint_timestamp text NOT NULL DEFAULT '',
    matched_item_id text,
    matched_item_name text,
    qty_deducted double precision NOT NULL DEFAULT 0.0,
    processed_at text NOT NULL DEFAULT '',
    colorant_used text NOT NULL DEFAULT '',
    colorant_quantity text NOT NULL DEFAULT ''
);

-- -----------------------------------------------------------------------------
-- 2. Membership & Admin Authorization Helper Functions
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.current_user_email()
RETURNS text
LANGUAGE sql
STABLE
AS $$
    SELECT lower(coalesce(auth.jwt() ->> 'email', ''));
$$;

CREATE OR REPLACE FUNCTION public.is_authorized_store_user()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT auth.role() = 'authenticated'
       AND public.current_user_email() <> ''
       AND EXISTS (
           SELECT 1
           FROM public.app_users u
           WHERE lower(u.email) = public.current_user_email()
       );
$$;

CREATE OR REPLACE FUNCTION public.is_store_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT auth.role() = 'authenticated'
       AND (
           coalesce((auth.jwt() -> 'app_metadata' ->> 'is_admin')::boolean, false) = true
           OR lower(coalesce(auth.jwt() -> 'app_metadata' ->> 'role', '')) = 'admin'
           OR EXISTS (
               SELECT 1
               FROM public.app_users u
               WHERE lower(u.email) = public.current_user_email()
                 AND u.is_admin = true
           )
       );
$$;

REVOKE ALL ON FUNCTION public.current_user_email() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.current_user_email() TO authenticated;

REVOKE ALL ON FUNCTION public.is_authorized_store_user() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_authorized_store_user() TO authenticated;

REVOKE ALL ON FUNCTION public.is_store_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_store_admin() TO authenticated;

-- -----------------------------------------------------------------------------
-- 3. Trigger Protection Against Client Privilege Escalation on `app_users`
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.guard_app_users_privilege_escalation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    NEW.email := lower(trim(NEW.email));
    NEW.updated_at := now();

    -- Allow privileged server contexts (service_role / postgres migrations)
    IF auth.role() IN ('service_role', 'supabase_admin') OR current_user IN ('postgres', 'supabase_admin') THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'INSERT' THEN
        -- Non-admins cannot create admin rows
        IF NEW.is_admin = true AND NOT public.is_store_admin() THEN
            RAISE EXCEPTION 'Only an existing store admin may grant admin privileges';
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        -- Non-admins cannot mutate is_admin or alter another user's record
        IF NOT public.is_store_admin() THEN
            IF NEW.is_admin IS DISTINCT FROM OLD.is_admin OR NEW.email IS DISTINCT FROM OLD.email THEN
                RAISE EXCEPTION 'Permission denied: cannot modify role or email of another user';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_app_users_privilege ON public.app_users;
CREATE TRIGGER trg_guard_app_users_privilege
BEFORE INSERT OR UPDATE ON public.app_users
FOR EACH ROW
EXECUTE FUNCTION public.guard_app_users_privilege_escalation();

-- Automatically provision newly confirmed/signed-up users as non-admin staff rows
-- (Admins must promote additional store staff or bootstrap the initial owner via Dashboard/SQL)
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NEW.email IS NOT NULL AND trim(NEW.email) <> '' THEN
        INSERT INTO public.app_users (email, is_admin)
        VALUES (lower(trim(NEW.email)), false)
        ON CONFLICT (email) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_auth_users_create_app_user ON auth.users;
CREATE TRIGGER trg_auth_users_create_app_user
AFTER INSERT ON auth.users
FOR EACH ROW
EXECUTE FUNCTION public.handle_new_auth_user();

-- Transactional self-service account deletion RPC
CREATE OR REPLACE FUNCTION public.delete_user_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_uid uuid := auth.uid();
    v_email text := lower(coalesce(auth.jwt() ->> 'email', ''));
BEGIN
    IF v_uid IS NULL OR v_email = '' THEN
        RAISE EXCEPTION 'Authenticated user session required';
    END IF;

    DELETE FROM public.app_users WHERE lower(email) = v_email;
    DELETE FROM auth.users WHERE id = v_uid;
END;
$$;

REVOKE ALL ON FUNCTION public.delete_user_account() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO authenticated;

-- -----------------------------------------------------------------------------
-- 4. Drop Existing Permissive / Anon Policies & Enable Strict RLS
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    tbl text;
    pol record;
    tables_list text[] := ARRAY[
        'items',
        'transactions',
        'purchases',
        'daily_cashflow',
        'quotations',
        'ledger_accounts',
        'ledger_entries',
        'audit_logs',
        'app_users',
        'store_profile',
        'gst_bills',
        'color_tint_logs'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables_list LOOP
        IF to_regclass('public.' || tbl) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
            EXECUTE format('REVOKE ALL ON public.%I FROM anon', tbl);
            FOR pol IN
                SELECT policyname
                FROM pg_policies
                WHERE schemaname = 'public' AND tablename = tbl
            LOOP
                EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, tbl);
            END LOOP;
        END IF;
    END LOOP;
END $$;

-- -----------------------------------------------------------------------------
-- 5. Strict Authenticated Store Policies (Zero Anon Access)
-- -----------------------------------------------------------------------------

-- `app_users`: authorized users can view roster; only admins can insert/update/delete users
CREATE POLICY "app_users_select_authorized"
    ON public.app_users FOR SELECT TO authenticated
    USING (public.is_authorized_store_user() OR lower(email) = public.current_user_email());

CREATE POLICY "app_users_insert_admin"
    ON public.app_users FOR INSERT TO authenticated
    WITH CHECK (public.is_store_admin());

CREATE POLICY "app_users_update_admin"
    ON public.app_users FOR UPDATE TO authenticated
    USING (public.is_store_admin())
    WITH CHECK (public.is_store_admin());

CREATE POLICY "app_users_delete_admin"
    ON public.app_users FOR DELETE TO authenticated
    USING (public.is_store_admin());

-- `store_profile`: authorized users can read branding/banking info; strictly admins can write
CREATE POLICY "store_profile_select_authorized"
    ON public.store_profile FOR SELECT TO authenticated
    USING (public.is_authorized_store_user());

CREATE POLICY "store_profile_insert_admin"
    ON public.store_profile FOR INSERT TO authenticated
    WITH CHECK (public.is_store_admin());

CREATE POLICY "store_profile_update_admin"
    ON public.store_profile FOR UPDATE TO authenticated
    USING (public.is_store_admin())
    WITH CHECK (public.is_store_admin());

CREATE POLICY "store_profile_delete_admin"
    ON public.store_profile FOR DELETE TO authenticated
    USING (public.is_store_admin());

-- `audit_logs`: append-only for authorized staff, readable only by admins, NO client UPDATE or DELETE
CREATE POLICY "audit_logs_insert_authorized"
    ON public.audit_logs FOR INSERT TO authenticated
    WITH CHECK (
        public.is_authorized_store_user()
        AND lower(coalesce(user_email, '')) = public.current_user_email()
    );

CREATE POLICY "audit_logs_select_admin"
    ON public.audit_logs FOR SELECT TO authenticated
    USING (public.is_store_admin());

-- Operational store tables: authorized store members only (requires active entry in `app_users`)
-- Note: `items` and `transactions` allow SELECT/INSERT/UPDATE for staff; destructive DELETE requires admin.
CREATE POLICY "items_select_authorized"
    ON public.items FOR SELECT TO authenticated
    USING (public.is_authorized_store_user());

CREATE POLICY "items_insert_authorized"
    ON public.items FOR INSERT TO authenticated
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "items_update_authorized"
    ON public.items FOR UPDATE TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "items_delete_admin"
    ON public.items FOR DELETE TO authenticated
    USING (public.is_store_admin());

CREATE POLICY "transactions_select_authorized"
    ON public.transactions FOR SELECT TO authenticated
    USING (public.is_authorized_store_user());

CREATE POLICY "transactions_insert_authorized"
    ON public.transactions FOR INSERT TO authenticated
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "transactions_delete_admin"
    ON public.transactions FOR DELETE TO authenticated
    USING (public.is_store_admin());

CREATE POLICY "purchases_all_authorized"
    ON public.purchases FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "daily_cashflow_all_authorized"
    ON public.daily_cashflow FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "quotations_all_authorized"
    ON public.quotations FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "ledger_accounts_all_authorized"
    ON public.ledger_accounts FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "ledger_entries_all_authorized"
    ON public.ledger_entries FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "gst_bills_all_authorized"
    ON public.gst_bills FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

CREATE POLICY "color_tint_logs_all_authorized"
    ON public.color_tint_logs FOR ALL TO authenticated
    USING (public.is_authorized_store_user())
    WITH CHECK (public.is_authorized_store_user());

-- -----------------------------------------------------------------------------
-- 6. Migrate Legacy `__STORE_PROFILE__` Sentinel From `purchases` (if present)
-- -----------------------------------------------------------------------------
INSERT INTO public.store_profile (id, profile_json, updated_at)
SELECT
    '00000000-0000-0000-0000-000000000001',
    (
        (COALESCE((lines::jsonb -> 0), '{}'::jsonb) - 'userRolesJson')::text
    ),
    COALESCE(((lines::jsonb -> 0 ->> 'updatedAt')::bigint), 0)
FROM public.purchases
WHERE client_id = '00000000-0000-0000-0000-000000000001'
  AND supplier = '__STORE_PROFILE__'
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 7. Realtime Publication for New Tables
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.store_profile;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.gst_bills;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
        BEGIN
            ALTER PUBLICATION supabase_realtime ADD TABLE public.color_tint_logs;
        EXCEPTION WHEN duplicate_object THEN NULL;
        END;
    END IF;
END $$;

COMMIT;
