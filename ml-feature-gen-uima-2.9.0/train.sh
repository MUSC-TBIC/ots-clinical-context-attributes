#!/bin/zsh

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_131.jdk/Contents/Home
export VERSION=21.40.3

############
## i2b2 2010

java -cp \
     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
     edu.musc.tbic.uima.FeatureGen \
     --pipeline-properties trainConfigs/pipeline_i2b2-train_trainSVM-i2b2.properties

java -cp \
     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
     edu.musc.tbic.uima.FeatureGen \
     --pipeline-properties trainConfigs/pipeline_i2b2-test_trainSVM-i2b2.properties

###########
## MUSC

java -cp \
     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
     edu.musc.tbic.uima.FeatureGen \
     --pipeline-properties trainConfigs/pipeline_MUSC-train_trainSVM-i2b2.properties

java -cp \
     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
     edu.musc.tbic.uima.FeatureGen \
     --pipeline-properties trainConfigs/pipeline_MUSC-test_trainSVM-i2b2.properties

###########
## Utah

java -cp \
     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
     edu.musc.tbic.uima.FeatureGen \
     --pipeline-properties trainConfigs/pipeline_Utah-train_trainSVM-i2b2.properties

java -cp \
     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
     edu.musc.tbic.uima.FeatureGen \
     --pipeline-properties trainConfigs/pipeline_Utah-test_trainSVM-i2b2.properties
