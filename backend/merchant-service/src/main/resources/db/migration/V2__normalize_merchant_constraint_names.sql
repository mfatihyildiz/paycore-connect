ALTER TABLE merchants
    RENAME CONSTRAINT uk5j287t79f6on0o4o2toa1v848 TO uk_merchants_api_key;

ALTER TABLE merchants
    RENAME CONSTRAINT ukgx9y1yah4qdijdi45ow7nxvdr TO uk_merchants_email;