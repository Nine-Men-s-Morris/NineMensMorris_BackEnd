CREATE USER 'user'@'localhost' IDENTIFIED BY 'password';
CREATE USER 'user'@'%' IDENTIFIED BY 'password';

GRANT ALL PRIVILEGES ON *.* TO 'user'@'localhost';
GRANT ALL PRIVILEGES ON *.* TO 'user'@'%';