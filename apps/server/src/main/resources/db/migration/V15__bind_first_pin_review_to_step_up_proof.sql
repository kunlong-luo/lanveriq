-- A11-C: bind each durable reviewer vote to a distinct step-up verifier
-- receipt. This is NOT proof a production WebAuthn/IdP verifier is installed:
-- the production verifier remains a hard deny.
--
-- Existing V14-only votes cannot silently become usable approvals. Add a NOT
-- VALID check: historical rows are left as untrusted history while every new
-- INSERT/UPDATE must contain complete evidence. A future signer must reject
-- old votes lacking step-up proof; no signer is wired at this stage.
ALTER TABLE first_pin_review_approvals
    ADD COLUMN step_up_proof_id UUID,
    ADD COLUMN step_up_verified_at TIMESTAMPTZ,
    ADD COLUMN step_up_method VARCHAR(16);

ALTER TABLE first_pin_review_approvals
    ADD CONSTRAINT first_pin_review_vote_requires_step_up
    CHECK (
        step_up_proof_id IS NOT NULL
        AND step_up_verified_at IS NOT NULL
        AND step_up_method = 'webauthn'
    ) NOT VALID;

CREATE UNIQUE INDEX first_pin_review_unique_step_up_proof
    ON first_pin_review_approvals(step_up_proof_id)
    WHERE step_up_proof_id IS NOT NULL;

-- No SQL trigger can independently determine that an IdP/WebAuthn assertion
-- was cryptographically verified. The only production verifier is deny-all,
-- and no review service is exposed through HTTP.
