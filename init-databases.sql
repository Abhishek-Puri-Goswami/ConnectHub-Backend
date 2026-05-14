-- Create all databases
CREATE DATABASE IF NOT EXISTS connecthub_auth;
CREATE DATABASE IF NOT EXISTS connecthub_room;
CREATE DATABASE IF NOT EXISTS connecthub_message;
CREATE DATABASE IF NOT EXISTS connecthub_media;
CREATE DATABASE IF NOT EXISTS connecthub_notification;
CREATE DATABASE IF NOT EXISTS connecthub_payment;

-- Create application user that services connect as
CREATE USER IF NOT EXISTS 'dbadmin'@'%' IDENTIFIED BY 'Anaya^123456';

-- Grant privileges on all service databases
GRANT ALL PRIVILEGES ON connecthub_auth.*         TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_room.*         TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_message.*      TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_media.*        TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_notification.* TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_payment.*      TO 'dbadmin'@'%';

FLUSH PRIVILEGES;
