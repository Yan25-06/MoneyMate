CREATE TABLE IF NOT EXISTS `users` (
    `id` TEXT NOT NULL,
    `email` TEXT,
    `display_name` TEXT,
    `hashed_passcode` TEXT,
    `currency` TEXT,
    `theme_mode` TEXT,
    `language` TEXT,
    `is_balance_hidden` INTEGER NOT NULL,
    `last_sync` INTEGER NOT NULL,
    `avatar_url` TEXT,
    `created_at` INTEGER NOT NULL,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `wallets` (
    `id` TEXT NOT NULL,
    `user_id` TEXT,
    `name` TEXT,
    `balance` REAL NOT NULL,
    `type` TEXT,
    `icon_name` TEXT NOT NULL,
    `is_archived` INTEGER NOT NULL,
    `is_excluded` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `sync_status` INTEGER NOT NULL,
    `is_deleted` INTEGER NOT NULL,
    `created_at` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `categories` (
    `id` TEXT NOT NULL,
    `user_id` TEXT,
    `name` TEXT,
    `type` TEXT,
    `icon_name` TEXT NOT NULL,
    `parent_id` TEXT,
    `wallet_id` TEXT,
    `is_default` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `sync_status` INTEGER NOT NULL,
    `is_deleted` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `debts` (
    `id` TEXT NOT NULL,
    `user_id` TEXT,
    `person_name` TEXT,
    `type` TEXT,
    `amount` REAL NOT NULL,
    `remaining_amount` REAL NOT NULL,
    `due_date` INTEGER,
    `status` TEXT,
    `note` TEXT,
    `updated_at` INTEGER NOT NULL,
    `sync_status` INTEGER NOT NULL,
    `is_deleted` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `events` (
    `id` TEXT NOT NULL,
    `user_id` TEXT,
    `name` TEXT,
    `budget_limit` REAL,
    `start_date` INTEGER NOT NULL,
    `end_date` INTEGER NOT NULL,
    `is_active` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `sync_status` INTEGER NOT NULL,
    `is_deleted` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `transactions` (
    `id` TEXT NOT NULL,
    `wallet_id` TEXT,
    `category_id` TEXT,
    `debt_id` TEXT,
    `event_id` TEXT,
    `amount` REAL NOT NULL,
    `type` TEXT,
    `to_wallet_id` TEXT,
    `note` TEXT,
    `timestamp` INTEGER NOT NULL,
    `image_path` TEXT,
    `updated_at` INTEGER NOT NULL,
    `sync_status` INTEGER NOT NULL,
    `is_deleted` INTEGER NOT NULL,
    `user_id` TEXT,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
    FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
    FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
    FOREIGN KEY(`to_wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS `budgets` (
    `id` TEXT NOT NULL,
    `category_id` TEXT,
    `user_id` TEXT,
    `amount` REAL NOT NULL,
    `start_date` INTEGER NOT NULL,
    `end_date` INTEGER NOT NULL,
    `wallet_id` TEXT,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `is_deleted` INTEGER NOT NULL,
    `sync_status` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE INDEX IF NOT EXISTS `index_categories_user_wallet_parent_type_deleted` ON `categories` (`user_id`, `wallet_id`, `parent_id`, `type`, `is_deleted`);
CREATE INDEX IF NOT EXISTS `index_wallets_user_archived_deleted` ON `wallets` (`user_id`, `is_archived`, `is_deleted`);
CREATE INDEX IF NOT EXISTS `index_transactions_wallet_id` ON `transactions` (`wallet_id`);
CREATE INDEX IF NOT EXISTS `index_transactions_category_id` ON `transactions` (`category_id`);
CREATE INDEX IF NOT EXISTS `index_transactions_debt_id` ON `transactions` (`debt_id`);
CREATE INDEX IF NOT EXISTS `index_transactions_event_id` ON `transactions` (`event_id`);
CREATE INDEX IF NOT EXISTS `index_transactions_to_wallet_id` ON `transactions` (`to_wallet_id`);
CREATE INDEX IF NOT EXISTS `index_transactions_user_id` ON `transactions` (`user_id`);
CREATE INDEX IF NOT EXISTS `index_budgets_user_id` ON `budgets` (`user_id`);
CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`);
CREATE INDEX IF NOT EXISTS `index_debts_user_id` ON `debts` (`user_id`);
CREATE INDEX IF NOT EXISTS `index_events_user_id` ON `events` (`user_id`);

INSERT INTO `users` (`id`, `email`, `display_name`, `hashed_passcode`, `currency`, `theme_mode`, `language`, `is_balance_hidden`, `last_sync`, `avatar_url`, `created_at`) VALUES
('u1', 'u1@test.local', 'User One', NULL, 'VND', 'system', 'vi', 0, 1710000000000, NULL, 0);

INSERT INTO `wallets` (`id`, `user_id`, `name`, `balance`, `type`, `icon_name`, `is_archived`, `is_excluded`, `updated_at`, `sync_status`, `is_deleted`, `created_at`) VALUES
('w1', 'u1', 'Cash', 1000000, 'CASH', 'ic_wallet_default', 0, 0, 1710000001000, 0, 0, 0);

INSERT INTO `categories` (`id`, `user_id`, `name`, `type`, `icon_name`, `parent_id`, `wallet_id`, `is_default`, `updated_at`, `sync_status`, `is_deleted`) VALUES
('c1', 'u1', 'Food', 'EXPENSE', 'ic_category_default', NULL, NULL, 0, 1710000002000, 0, 0);

INSERT INTO `debts` (`id`, `user_id`, `person_name`, `type`, `amount`, `remaining_amount`, `due_date`, `status`, `note`, `updated_at`, `sync_status`, `is_deleted`) VALUES
('d1', 'u1', 'Alice', 'BORROW', 100000, 100000, NULL, 'ACTIVE', 'note', 1710000003000, 0, 0);

INSERT INTO `events` (`id`, `user_id`, `name`, `budget_limit`, `start_date`, `end_date`, `is_active`, `updated_at`, `sync_status`, `is_deleted`) VALUES
('e1', 'u1', 'Trip', 2000000, 1710000000000, 1712600000000, 1, 0, 0, 0);

INSERT INTO `transactions` (`id`, `wallet_id`, `category_id`, `debt_id`, `event_id`, `amount`, `type`, `to_wallet_id`, `note`, `timestamp`, `image_path`, `updated_at`, `sync_status`, `is_deleted`, `user_id`) VALUES
('t1', 'w1', 'c1', NULL, NULL, 25000, 'EXPENSE', NULL, 'Lunch', 1710000004000, NULL, 0, 0, 0, 'u1');

INSERT INTO `budgets` (`id`, `category_id`, `user_id`, `amount`, `start_date`, `end_date`, `wallet_id`, `created_at`, `updated_at`, `is_deleted`, `sync_status`) VALUES
('b1', 'c1', 'u1', 500000, 1710000000000, 1712600000000, NULL, 0, 1710000005000, 0, 0);

