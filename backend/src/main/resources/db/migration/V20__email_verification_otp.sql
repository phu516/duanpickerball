alter table email_verification_tokens add column otp_nonce varchar(32);
alter table email_verification_tokens add column attempts int not null default 0;

-- Existing link tokens are retired; pending accounts can request a fresh OTP.
update email_verification_tokens set used_at=current_timestamp(6) where used_at is null;
