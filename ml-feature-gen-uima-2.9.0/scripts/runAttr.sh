libHome='/Users/jun/Documents/work/liblinear-2.20'

ftFile='/Users/jun/Documents/work/i2b2/ut_al/fv_out/ut_al_attr_ft.txt'

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

	trFile=$libHome'/ut_al_attr_tr_ml'	
	tsFile=$libHome'/ut_al_attr_ts_ml'	
	predFile=$libHome'/ut_al_attr_pred_ml.txt' 
  modelFile=$libHome'/ut_al_attr_model_ml'

	java -Xmx10g -cp $libHome LIBTrFV2AttrML 1 $trOrig $trFile $bow 1 $ftFile $lFile $loFile
	java -Xmx10g -cp $libHome LIBTrFV2AttrML 0 $tsOrig $tsFile $bow 1 $ftFile $lFile $loFile
	
	#$libHome/train -s 2 -C -c 0.01 $trFile $modelFile
	$libHome/train -s 2 -q -c 0.0625 $trFile $modelFile
	$libHome/predict $tsFile $modelFile $predFile
	
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

	trFile=$libHome'/ut_al_attr_tr'	
	tsFile=$libHome'/ut_al_attr_ts'	
	predFile=$libHome'/ut_al_attr_pred.txt' 
  modelFile=$libHome'/ut_al_attr_model'

	idx=1
	
	while [ $idx -le 6 ]
	do
		echo $idx

		java -Xmx10g -cp $libHome LIBTrFV2Attr 1 $trOrig $trFile $bow 1 $ftFile $lFile $idx
		java -Xmx10g -cp $libHome LIBTrFV2Attr 0 $tsOrig $tsFile $bow 1 $ftFile $lFile $idx
	
		$libHome/train -s 2 -q $trFile"_"$idx $modelFile"_"$idx
		$libHome/predict $tsFile"_"$idx $modelFile"_"$idx $predFile"_"$idx
	
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
run2010=n
if [ "$run2010" == "y" ]
then

	fvHome='/Users/jun/Documents/work/i2b2/2010rel/fv_out'
	trOrig=$fvHome'/2010_attr_tr.txt'
	tsOrig=$fvHome'/2010_attr_ts.txt'
	tsLbl=$fvHome'/2010_attr_ts.txt.lbl'
	bow=$fvHome'/2010_attr_bow.txt'
	lFile=$fvHome'/attr_class.txt'

	trFile=$libHome'/2010_attr_tr'	
	tsFile=$libHome'/2010_attr_ts'	
	predFile=$libHome'/2010_attr_pred.txt' 
  modelFile=$libHome'/2010_attr_model'

	java -Xmx10g -cp $libHome LIBTrFV2AttrMulti 1 $trOrig $trFile $bow 1 $ftFile $lFile
	time java -Xmx10g -cp $libHome LIBTrFV2AttrMulti 0 $tsOrig $tsFile $bow 1 $ftFile $lFile
	
	#$libHome/train -s 2 -v 10 -q $trFile $modelFile
	
	$libHome/train -s 2 -q $trFile $modelFile
	time $libHome/predict $tsFile $modelFile $predFile
	
	refDir='/Users/jun/Documents/work/i2b2/2010rel/test/ref/'
	conDir='/Users/jun/Documents/work/i2b2/2010rel/test/ref/'
	ansDir='/Users/jun/Documents/work/i2b2/2010rel/test/attrSvm/'
	
	mkdir -p $ansDir
	rm -rf $ansDir/*
	
	java -Xmx4g AttrOutputAlMulti $tsLbl $predFile $conDir $ansDir 1 $lFile
	java -Xmx1g EvalAlAttrMulti $refDir $ansDir
		
fi
