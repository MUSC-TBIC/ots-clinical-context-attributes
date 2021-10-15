#!/bin/bash

## By default, don't do anything.  Flip these variables to run the
## given corpus' train/test script
run2010=n
run2010musc=n
run2010utah=n
run2010All=n

if [[ -z $LIBLINEARHOME ]]; then
    echo "The variable \$LIBLINEARHOME is not set. Using default."
    export LIBLINEARHOME='/Users/pmh/bin/liblinear'
fi

if [[ -z $RESOURCES_DIR ]]; then
    echo "The variable \$RESOURCES_DIR is not set. Using default."
    export RESOURCES_DIR='resources'
fi

if [[ -z $FEATFILE ]]; then
    echo "The variable \$FEATFILE is not set. Using default."
    export FEATFILE='resources/ut_al_attr_ft.txt'
fi

###########################################
# i2b2 2010 corpus
if [ "$run2010" == "y" ]
then

    trainHome='data/train/out/i2b2-SVM/features'
    testHome='data/test/out/i2b2-SVM/features'
    trOrig=$trainHome'/2010_attr_train.txt'
    tsOrig=$testHome'/2010_attr_test.txt'
    tsLbl=$testHome'/2010_attr_test.txt.lbl'
    bow=$trainHome'/2010_attr_bow.txt'
    combinedLblFile=$RESOURCES_DIR'/attr_class_combined.txt'
    i2b2LblFile=$RESOURCES_DIR'/attr_class_i2b2.txt'

    trFile=$LIBLINEARHOME'/2010_attr_train'
    tsFile=$LIBLINEARHOME'/2010_attr_test'
    predFile=$LIBLINEARHOME'/2010_attr_pred.txt'
    modelFile=$RESOURCES_DIR'/liblinear/2010_attr.model'

    echo "Converting train corpus..."
    java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 1 $trOrig $trFile $bow 1 $FEATFILE $combinedLblFile
    echo "Converting test corpus..."
    time java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 0 $tsOrig $tsFile $bow 1 $FEATFILE $combinedLblFile
    
    echo "Training model..."
    $LIBLINEARHOME/train -s 2 -q $trFile $modelFile
    echo "Predicting model..."
    time $LIBLINEARHOME/predict $tsFile $modelFile $predFile
    
    # refDir='/Users/jun/Documents/work/i2b2/2010rel/test/ref/'
    conDir='/Users/pmh/Box Sync/TBIC/2010 i2b2 challenge - rel/test/ref'
    ansDir='data/test/out/pred2010'
    
    mkdir -p $ansDir
    rm $ansDir/*

    echo "Converting prediction output..."
    java -Xmx4g -cp scripts AttrOutputAlMulti $tsLbl $predFile "$conDir" $ansDir 1 $i2b2LblFile
    echo "Scoring model..."
    java -Xmx1g -cp scripts EvalAlAttrMulti "$conDir" $ansDir

fi

######################
# MUSC formatted like i2b2 2010 corpus
if [ "$run2010musc" == "y" ]
then

    trainHome='data/train/out/musc-SVM/features'
    testHome='data/test/out/musc-SVM/features'
    trOrig=$trainHome'/musc_attr_train.txt'
    tsOrig=$testHome'/musc_attr_test.txt'
    tsLbl=$testHome'/musc_attr_test.txt.lbl'
    bow=$trainHome'/musc_attr_bow.txt'
    combinedLblFile=$RESOURCES_DIR'/attr_class_combined.txt'
    i2b2LblFile=$RESOURCES_DIR'/attr_class_i2b2.txt'

    trFile=$LIBLINEARHOME'/musc_attr_train'
    tsFile=$LIBLINEARHOME'/musc_attr_test'
    predFile=$LIBLINEARHOME'/musc_attr_pred.txt'
    modelFile=$RESOURCES_DIR'/liblinear/musc-i2b2_attr.model'

    echo "Converting train corpus..."
    java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 1 $trOrig $trFile $bow 1 $FEATFILE $combinedLblFile
    echo "Converting test corpus..."
    time java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 0 $tsOrig $tsFile $bow 1 $FEATFILE $combinedLblFile
    
    #$LIBLINEARHOME/train -s 2 -v 10 -q $trFile $modelFile

    echo "Training model..."
    $LIBLINEARHOME/train -s 2 -q $trFile $modelFile
    echo "Predicting model..."
    time $LIBLINEARHOME/predict $tsFile $modelFile $predFile
    
    # There is a ref format mismatch so we can't do a direct evaluation.
    # Instead, the destroy() function generates our confusion matrix scores.

fi

######################
# Utah formatted like i2b2 2010 corpus
if [ "$run2010utah" == "y" ]
then

    trainHome='data/train/out/utah-SVM/features'
    testHome='data/test/out/utah-SVM/features'
    trOrig=$trainHome'/utah_attr_train.txt'
    tsOrig=$testHome'/utah_attr_test.txt'
    tsLbl=$testHome'/utah_attr_test.txt.lbl'
    bow=$trainHome'/utah_attr_bow.txt'
    combinedLblFile=$RESOURCES_DIR'/attr_class_combined.txt'
    i2b2LblFile=$RESOURCES_DIR'/attr_class_i2b2.txt'

    trFile=$LIBLINEARHOME'/utah_attr_train'
    tsFile=$LIBLINEARHOME'/utah_attr_test'
    predFile=$LIBLINEARHOME'/utah_attr_pred.txt'
    modelFile=$RESOURCES_DIR'/liblinear/utah-i2b2_attr.model'

    echo "Converting train corpus..."
    java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 1 $trOrig $trFile $bow 1 $FEATFILE $combinedLblFile
    echo "Converting test corpus..."
    time java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 0 $tsOrig $tsFile $bow 1 $FEATFILE $combinedLblFile
    
    #$LIBLINEARHOME/train -s 2 -v 10 -q $trFile $modelFile

    echo "Training model..."
    $LIBLINEARHOME/train -s 2 -q $trFile $modelFile
    echo "Predicting model..."
    time $LIBLINEARHOME/predict $tsFile $modelFile $predFile
    
    # There is a ref format mismatch so we can't do a direct evaluation.
    # Instead, the destroy() function generates our confusion matrix scores.

fi

######################
# i2b2, MUSC, and Utah formatted like i2b2 2010 corpus

#mkdir data/train/out/composite-SVM
#mkdir data/train/out/composite-SVM/features
#mkdir data/test/out/composite-SVM
#mkdir data/test/out/composite-SVM/features

#cat data/train/out/{i2b2,musc,utah}-SVM/features/*_attr_train.txt \
#    > data/train/out/composite-SVM/features/composite_attr_train.txt
#cat data/test/out/{i2b2,musc,utah}-SVM/features/*_attr_test.txt \
#    > data/test/out/composite-SVM/features/composite_attr_test.txt
#cat data/test/out/{i2b2,musc,utah}-SVM/features/*_attr_test.txt.lbl \
#    > data/test/out/composite-SVM/features/composite_attr_test.txt.lbl

if [ "$run2010All" == "y" ]
then

    trainHome='data/train/out/composite-SVM/features'
    testHome='data/test/out/composite-SVM/features'
    trOrig=$trainHome'/composite_attr_train.txt'
    tsOrig=$testHome'/composite_attr_test.txt'
    tsLbl=$testHome'/composite_attr_test.txt.lbl'
    bow=$trainHome'/composite_attr_bow.txt'
    combinedLblFile=$RESOURCES_DIR'/attr_class_combined.txt'

    trFile=$LIBLINEARHOME'/composite_attr_train'
    tsFile=$LIBLINEARHOME'/composite_attr_test'
    predFile=$LIBLINEARHOME'/composite_attr_pred.txt'
    modelFile=$RESOURCES_DIR'/liblinear/composite-i2b2_attr.model'

    echo "Converting train corpus..."
    java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 1 $trOrig $trFile $bow 1 $FEATFILE $combinedLblFile
    echo "Converting test corpus..."
    time java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 0 $tsOrig $tsFile $bow 1 $FEATFILE $combinedLblFile

    echo "Training model..."
    $LIBLINEARHOME/train -s 2 -q $trFile $modelFile
    echo "Predicting model..."
    time $LIBLINEARHOME/predict $tsFile $modelFile $predFile
    
    # There is a ref format mismatch so we can't do a direct evaluation.
    # Instead, the destroy() function generates our confusion matrix scores.
    
fi
