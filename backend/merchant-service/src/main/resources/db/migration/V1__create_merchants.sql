CREATE TABLE merchants (
                           id UUID NOT NULL,
                           api_key VARCHAR(100) NOT NULL,
                           created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
                           email VARCHAR(150) NOT NULL,
                           name VARCHAR(150) NOT NULL,
                           status VARCHAR(30) NOT NULL,
                           updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,

                           CONSTRAINT merchants_pkey PRIMARY KEY (id),
                           CONSTRAINT uk5j287t79f6on0o4o2toa1v848 UNIQUE (api_key),
                           CONSTRAINT ukgx9y1yah4qdijdi45ow7nxvdr UNIQUE (email),
                           CONSTRAINT merchants_status_check
                               CHECK (status IN ('ACTIVE', 'PASSIVE', 'SUSPENDED'))
);