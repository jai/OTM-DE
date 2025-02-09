#Overview

OTM-DE (Open Travel Model - Development Environment)

##Dependencies
* [Java >= 1.6](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Maven 3.0.X](http://maven.apache.org/)


##Build

* Build Schema Compiler
* Run the following command in the `/target-definition/local-p2-site` folder:

__Run:__
    $ mvn p2:site
    $ mvn jetty:run #will run local update site, do not close until target definition will be resolved (see next steps)

* Start a new shell and run the following command in the root folder:

    $ mvn clean install

* Executables files will be created in `/product/target/products`.

##Setting up a development environment

* Download [Eclipse for RCP and RAP Developers](http://www.eclipse.org/downloads/packages/eclipse-rcp-and-rap-developers/keplersr1).
* Start Eclipse and import existing Maven projects `(Import -> Existing Maven Projects)`.
* Make sure you have a running Jetty server with the local repository:

__Run:__
    $ cd /target-definition/local-p2-site
    $ mvn p2:site
    $ mvn jetty:run
* Open the target definition `indigo/indigo.target` and click **Set as Target Platform** in the upper right corner.
* To run **OTM-DE** from Eclipse use pre-configured luncher configuration: `/product/OT2Editor.product.launch`.
