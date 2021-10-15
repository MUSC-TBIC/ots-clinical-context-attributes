
Building
========



Python Set-Up
-------------

.. code:: bash

   conda create -n word-embeddings-py3.7 python=3.7
   conda activate word-embeddings-py3.7
	  
Java Set-Up
-----------

.. code:: bash

   export JAVA_HOME=/path/to/jdk1.8.0_131.jdk/Contents/Home
   
   export UIMA_HOME=/path/to/apache-uima-2.9.0
   export PATH=$PATH:$UIMA_HOME/bin
   
   export CTAKES_HOME="/path/to/apache-ctakes-4.0.0"

   export PIPELINE_ROOT=/path/to/ots-clinical-context-attributes/ml-feature-gen-uima-2.9.0
   
   export UIMA_CLASSPATH=${PIPELINE_ROOT}/target/classes
   export UIMA_CLASSPATH=$UIMA_CLASSPATH:${PIPELINE_ROOT}/lib
   export UIMA_CLASSPATH=${UIMA_CLASSPATH}:${CTAKES_HOME}/lib:${CTAKES_HOME}/resources
   export UIMA_CLASSPATH=${UIMA_CLASSPATH}:${PIPELINE_ROOT}/resources
   
   export UIMA_DATAPATH=${PIPELINE_ROOT}/resources
   
   export UIMA_JVM_OPTS="-Xms128M -Xmx2G"
   
   cd ${PIPELINE_ROOT}/resources/openNlpModels
   curl -Lfs --output 'en-token.bin' 'http://opennlp.sourceforge.net/models-1.5/en-token.bin'
   curl -Lfs --output 'en-pos-maxent.bin' 'http://opennlp.sourceforge.net/models-1.5/en-pos-maxent.bin'
   
   cd ${PIPELINE_ROOT}/resources/ctakesModels
   curl -Lfs --output 'sd-med-model.zip' \
       'https://github.com/apache/ctakes/tree/trunk/ctakes-core-res/src/main/resources/org/apache/ctakes/core/sentdetect/sd-med-model.zip

   cd ${PIPELINE_ROOT}
   mvn package


Running
=======

A sample scripts is available under:

- ml-feature-gen-uima-2.9.0/train.sh

- ml-feature-gen-uima-2.9.0/test.sh

- ml-feature-gen-uima-2.9.0/scripts/runAttr.sh

.. code:: bash

   cp resources/pipeline.properties.TEMPLATE resources/pipeline.properties
   
   export JAVA_HOME=/path/to/jdk1.8.0_131.jdk/Contents/Home
   export VERSION=21.40.3
   
   java -cp \
       resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
       edu.musc.tbic.uima.FeatureGen -h

   java -cp \
       resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
       edu.musc.tbic.uima.FeatureGen \
       --pipeline-properties pipeline.properties

