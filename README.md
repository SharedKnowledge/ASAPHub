# Maven Guide

If you are using the Intellij IDE it's **not necessary
to have maven installed on your local machine**. 
You can press the [Ctrl key twice](https://www.jetbrains.com/help/idea/work-with-maven-goals.html) to open the maven run window.
Inside this window you can run the maven commands.

## compile, run tests and build the latest ASAPHub jar

`mvn clean package`

This compiles all files, runs tests and, if they succeed, compiles the jar. 
The compiled jar (ASAPHub.jar) will be in your target/ folder.

### without tests

This is needed in case you want to build the jar while your tests fail (which need some time for execution
).
`mvn clean package -DskipTests`

### only run tests
Run all testcases to perform a healthcheck:

`mvn test`

## update ASAPJava library

The ASAPJava dependency is installed in the local maven repository which is located at `local_mvn_repository`.
Maven resolves the ASAPJava dependency using this local repository. If you are using the Intellij IDE it's **not necessary
to have maven installed on your local machine**. You can press the [Ctrl key twice](https://www.jetbrains.com/help/idea/work-with-maven-goals.html) to open the maven run window. 
Here you can paste the mentioned maven commands. 
To update the ASAPJava library the following steps are necessary:
1. replace `libs/ASAPJava.jar` by a newer release 
2. install new jar to the local maven repository
```shell
mvn install:install-file \
  -Dfile=libs/ASAPJava.jar \
  -DgroupId=net.sharksystem \
  -DartifactId=asapjava \
  -Dversion=<<insert_version>> \
  -Dpackaging=jar \
  -DgeneratePom=true \
  -DlocalRepositoryPath=local_mvn_repo \
  -DrepositoryId=local-maven-repo
```
3. update the version number of ASAPJava dependency inside the `pom.xml`:
```xml
...
 <dependencies>
        ...
        <dependency>
            <groupId>net.sharksystem</groupId>
            <artifactId>asapjava</artifactId>
            <version>***insert_version**</version>
        </dependency>
    </dependencies>
...
```
4. sync local repository with the .m2 repository of your machine `mvn clean install -U -DskipTests`
5. commit changes in `local_mvn_repo` and `pom.xml`

# Build and run using Docker

## build image

`docker build -t asaphub:latest .`

## run container

`docker run --rm --net=host --name hub asaphub:latest`

# Wiki

Visit [the wiki](https://github.com/SharedKnowledge/ASAPHub/wiki) for all other information on what ASAP is and how to use it.
