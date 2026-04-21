-- Create all databases
CREATE DATABASE IF NOT EXISTS connecthub_auth;
CREATE DATABASE IF NOT EXISTS connecthub_room;
CREATE DATABASE IF NOT EXISTS connecthub_message;
CREATE DATABASE IF NOT EXISTS connecthub_media;
CREATE DATABASE IF NOT EXISTS connecthub_notification;
CREATE DATABASE IF NOT EXISTS connecthub_payment;

-- Create application user that services connect as
CREATE USER IF NOT EXISTS 'parents'@'%' IDENTIFIED BY 'parents@top';

-- Grant privileges on all service databases
GRANT ALL PRIVILEGES ON connecthub_auth.*         TO 'parents'@'%';
GRANT ALL PRIVILEGES ON connecthub_room.*         TO 'parents'@'%';
GRANT ALL PRIVILEGES ON connecthub_message.*      TO 'parents'@'%';
GRANT ALL PRIVILEGES ON connecthub_media.*        TO 'parents'@'%';
GRANT ALL PRIVILEGES ON connecthub_notification.* TO 'parents'@'%';
GRANT ALL PRIVILEGES ON connecthub_payment.*      TO 'parents'@'%';

FLUSH PRIVILEGES;
