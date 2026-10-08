# Welcome to Parts Manager PLM
Parts Management done right, made by engineers for engineers

# Server installation (Linux/Mac):
## Bare Metal Installation

Download the server
```bash
$ wget https://github.com/redshift2/Parts_Manager_PLM/releases/download/v1/server-0.6.jar
```

Check Java version > 25:
```bash
$ java -fullversion
openjdk full version "25.0.1"
```

Launch it:
```bash
$ java -jar server-0.6.jar
```

## Build From Source
```SH
 $ mkdir parts-manager
 $ cd parts-manager
 $ git clone https://github.com/redshift2/Parts_Manager_PLM.git
 $ git clone https://github.com/redshift2/Parts_manager_intranet_module.git
 $ cd Parts_manager_intranet_module/app/
 $ ln -s ../../Parts_Manager_PLM plm
 $ cd ..
 $ ./gradlew bootJar
 $ cp server/build/libs/server-0.6.jar ..
 $ cd ..
```
 
## Docker
This assumes that you have a working docker installation, see this web page for docker installation https://docs.docker.com/engine/install/

Download the ***docker-compose.yml*** file or copy it from below and adjust the paths and port to suit your needs
```yaml
services:
  base:
    build:
      context: .
      dockerfile_inline: |
        FROM eclipse-temurin:25
        RUN mkdir /opt/app
        RUN mkdir /database
        RUN touch /usr/bin/dot
        RUN touch /usr/bin/convert
        RUN chmod +x /usr/bin/dot
        RUN chmod +x /usr/bin/convert
        COPY server-0.6.jar /opt/app
        CMD ["sh", "-c", "java -Dgrails.env=production -DdataSource.url='jdbc:h2:/database/taack.db;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE' -Dgrails.controllers.upload.maxFileSize=$${MAX_UPLOAD_SIZE} -Dgrails.controllers.upload.maxRequestSize=$${MAX_UPLOAD_SIZE} -Dgrails.serverURL=$${HOST_URL}} -jar /opt/app/server-0.6.jar"] 

    container_name: taack-plm
    restart: unless-stopped
    ports:
      - 9442:9442
    environment:
      MAX_UPLOAD_SIZE: 1073741824  #set the maximum upload file size in bytes (This is set to 1gb)
      HOST_URL: http://<server ip>:<port>   #set the server domain name. Port is not required if using a reverse proxy
                                            #examples https://taackplm.org http://taackplm.org:9442 or 192.168.1.20:9442
    volumes:
      - ./partsmanager-plm/database:/database
      - ./partsmanager-plm/vault:/root/intranetFilesDev

```

Download the server to the same location as the docker-compose.yml file
```bash
$ wget https://github.com/redshift2/Parts_Manager_PLM/releases/download/v1/server-0.6.jar
```
Build the docker image
```bash
$ sudo docker compose build
```

Deloy the container 
```bash
$ sudo docker compose up -d
```


# You are done
access the server [http://localhost:9442/](http://localhost:9442/), connect with `admin` / `ChangeIt` credentials.
