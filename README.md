# Dockerswarm AWS assement project

This is a heavily modified version the demo project shown at DockerCon EU 2017.

The app runs across three containers:

- [db](db/Dockerfile) - a Postgres database which stores the conversion rate values

- [feed](feed/Dockerfile) - a Java feed that serves average exchange rates based on what is in the database

- [collector](collector/Dockerfile) - a recurring Java task that fills the database every 10 minutes

## Build

Using docker compose we build the app to run it in the swarm:

```
docker-compose build
```

## Deploy as a Docker Stack

Docker lets you use the simple [Docker Compose](https://docs.docker.com/compose/) file format to deploy complex applications to Kubernetes.
You can deploy the wordsmith app to the local Kubernetes cluster using [docker-compose.yml](docker-compose.yml).

Deploy the app to Kubernetes as a stack using the [compose file](docker-compose.yml):

```
docker stack rm assessment
docker stack deploy assessment -c docker-compose.yml
```

Once running the Java code will fill the database with at least 10 values if none existed yet.
It will also add an additional entry to the database every 10 minutes.

Once per hour it will look at the 10 most recent database entries, avarage them and output the averages.
