alter table password_reset_tokens add column otp_nonce varchar(32);
alter table password_reset_tokens add column attempts int not null default 0;
alter table password_reset_tokens add column verified_at datetime(6);
alter table password_reset_tokens add column reset_ticket_hash varchar(64);
alter table password_reset_tokens add column reset_ticket_expires_at datetime(6);
alter table password_reset_tokens add constraint uq_password_reset_ticket unique (reset_ticket_hash);

-- Old email links cannot be used after switching to OTP recovery.
update password_reset_tokens set used_at=current_timestamp(6) where used_at is null;
