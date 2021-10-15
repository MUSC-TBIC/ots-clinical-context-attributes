#!/bin/zsh

export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_131.jdk/Contents/Home
export VERSION=21.40.3

process_corpora() {
    echo "* $MODELFILE" >> /dev/stderr
    for CORPUS in i2b2-test MUSC-test Utah-test CovidNLP PCORI-COVID; do \
	echo "** $CORPUS" >> /dev/stderr; \
	echo "*** System.err" >> /dev/stderr; \
	cat resources/testConfigs/pipeline_${CORPUS}_SVM-i2b2.properties.TEMPLATE \
	    | envsubst \
		  > resources/pipeline_${CORPUS}_SVM-i2b2.properties; \
	java -cp \
	     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
	     edu.musc.tbic.uima.FeatureGen \
	     --pipeline-properties pipeline_${CORPUS}_SVM-i2b2.properties; done
}

process_fastcontext() {
    echo "* FastContext" >> /dev/stderr
    for CORPUS in i2b2-test MUSC-test Utah-test CovidNLP PCORI-COVID; do \
	echo "** $CORPUS" >> /dev/stderr; \
	echo "*** System.err" >> /dev/stderr; \
	java -cp \
	     resources:target/classes:target/ml-feature-gen-${VERSION}-SNAPSHOT-jar-with-dependencies.jar \
	     edu.musc.tbic.uima.FeatureGen \
	     --pipeline-properties testConfigs/pipeline_${CORPUS}_FastContext.properties; done
}

export MODELFILE=2010_attr.model
export BOWMAP=data/train/out/i2b2-SVM/features/2010_attr_bow.txt

process_corpora

export MODELFILE=musc-i2b2_attr.model
export BOWMAP=data/train/out/musc-SVM/features/musc_attr_bow.txt

process_corpora

export MODELFILE=utah-i2b2_attr.model
export BOWMAP=data/train/out/utah-SVM/features/utah_attr_bow.txt

process_corpora

export MODELFILE=composite-i2b2_attr.model
export BOWMAP=data/train/out/composite-SVM/features/composite_attr_bow.txt

process_corpora

process_fastcontext
