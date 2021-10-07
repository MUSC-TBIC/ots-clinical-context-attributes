#!/bin/bash

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

# multi-label svm for utah corpus
runUtahML=n
if [ "$runUtahML" == "y" ]
then

    fvHome='/Users/jun/Documents/work/i2b2/ut_al/fv_out'
    trOrig=$fvHome'/ut_al_attr_tr.txt'
    tsOrig=$fvHome'/ut_al_attr_ts.txt'
    tsLbl=$fvHome'/ut_al_attr_ts.txt.lbl'
    bow=$fvHome'/ut_al_attr_bow.txt'
    lFile=$fvHome'/attr_class.txt'
    loFile=$fvHome'/attr_class_out.txt'

    trFile=$LIBLINEARHOME'/ut_al_attr_tr_ml'	
    tsFile=$LIBLINEARHOME'/ut_al_attr_ts_ml'	
    predFile=$LIBLINEARHOME'/ut_al_attr_pred_ml.txt' 
    modelFile=$RESOURCES_DIR'/liblinear/ut_al_attr_model_ml'

    java -Xmx10g -cp $LIBLINEARHOME LIBTrFV2AttrML 1 $trOrig $trFile $bow 1 $FEATFILE $lFile $loFile
    java -Xmx10g -cp $LIBLINEARHOME LIBTrFV2AttrML 0 $tsOrig $tsFile $bow 1 $FEATFILE $lFile $loFile
    
    #$LIBLINEARHOME/train -s 2 -C -c 0.01 $trFile $modelFile
    $LIBLINEARHOME/train -s 2 -q -c 0.0625 $trFile $modelFile
    $LIBLINEARHOME/predict $tsFile $modelFile $predFile
    
    refDir='/Users/jun/Documents/work/i2b2/ut_al/test/ref/'
    conDir='/Users/jun/Documents/work/i2b2/ut_al/test/ref/'
    ansDir='/Users/jun/Documents/work/i2b2/ut_al/test/attr_ml/'
    
    mkdir -p $ansDir
    rm -rf $ansDir/*
    
    java -Xmx4g AttrOutputAlML $tsLbl $predFile $conDir $ansDir 1 $loFile
    java -Xmx1g EvalAlAttr $refDir $ansDir
    
fi


# multiple binary SVM for utah corpus
runUtah=n
if [ "$runUtah" == "y" ]
then

    fvHome='/Users/jun/Documents/work/i2b2/ut_al/fv_out'
    trOrig=$fvHome'/ut_al_attr_tr.txt'
    tsOrig=$fvHome'/ut_al_attr_ts.txt'
    tsLbl=$fvHome'/ut_al_attr_ts.txt.lbl'
    bow=$fvHome'/ut_al_attr_bow.txt'
    lFile=$fvHome'/attr_class.txt'

    trFile=$LIBLINEARHOME'/ut_al_attr_tr'	
    tsFile=$LIBLINEARHOME'/ut_al_attr_ts'	
    predFile=$LIBLINEARHOME'/ut_al_attr_pred.txt'
    modelFile=$RESOURCES_DIR'/liblinear/ut_al_attr_model'

    idx=1
    
    while [ $idx -le 6 ]
    do
	echo $idx

	java -Xmx10g -cp $LIBLINEARHOME LIBTrFV2Attr 1 $trOrig $trFile $bow 1 $FEATFILE $lFile $idx
	java -Xmx10g -cp $LIBLINEARHOME LIBTrFV2Attr 0 $tsOrig $tsFile $bow 1 $FEATFILE $lFile $idx
	
	$LIBLINEARHOME/train -s 2 -q $trFile"_"$idx $modelFile"_"$idx
	$LIBLINEARHOME/predict $tsFile"_"$idx $modelFile"_"$idx $predFile"_"$idx
	
	refDir='/Users/jun/Documents/work/i2b2/ut_al/test/ref/'
	conDir='/Users/jun/Documents/work/i2b2/ut_al/test/ref/'
	ansDir='/Users/jun/Documents/work/i2b2/ut_al/test/attr/'
	
	if [ $idx -eq 1 ]
	then
	    mkdir -p $ansDir
	    rm -rf $ansDir/*
	    
	    # 1 first: no Attr addition, 0 not first: add previous Attrs
	    java -Xmx4g AttrOutputAl $tsLbl $predFile"_"$idx $conDir $ansDir 1 1 $lFile $idx
	else
	    java -Xmx4g AttrOutputAl $tsLbl $predFile"_"$idx $conDir $ansDir 1 0 $lFile $idx
	fi
	
	if [ $idx -eq 6 ]
	then
	    java -Xmx1g EvalAlAttr $refDir $ansDir
	fi
	
	idx=$(( idx+1 ))	
	
    done
    
fi


# i2b2 2010 corpus
run2010=y
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
    modelFile=$RESOURCES_DIR'/liblinear/2010_attr_model'

    echo "Converting train corpus..."
    java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 1 $trOrig $trFile $bow 1 $FEATFILE $combinedLblFile
    echo "Converting test corpus..."
    time java -Xmx10g -cp $LIBLINEARHOME:scripts LIBTrFV2AttrMulti 0 $tsOrig $tsFile $bow 1 $FEATFILE $combinedLblFile
    
    #$LIBLINEARHOME/train -s 2 -v 10 -q $trFile $modelFile

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
