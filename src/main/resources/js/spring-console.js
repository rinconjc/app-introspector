// spring-console script

function initSpringConsole(){
    var cm;
    var scriptStore;
    function postScript(text){
	$('#spinner').show();
	$('#exec-info').text('');
	$("#resultText").text('');
	var start = new Date().getTime();
	$.ajax({
	    type:'POST',
	    url:'/spring/run',
	    contentType:'text/plain',
	    data:text,
	    success:function(data){
		$("#resultText").text(typeof(data) == 'string'?data:JSON.stringify(data, null, 4));
		var end = new Date();
		$('#exec-info').text('completed in '+ duration(end.getTime()-start) + ' at ' + end.getHours() + ':' +
				     end.getMinutes() + ':' + end.getSeconds() + '.' + end.getMilliseconds());
		$('#spinner').hide();
	    }
	});
    }

    function duration(ms){
	if(ms<1000) return ms + ' msecs';
	if(ms<60000) return (ms/1000).toFixed(2) + ' secs';
	return (ms/60000).toFixed(2) + ' mins';
    }

    function showBeanInfo(beanId){
	$.getJSON('/spring/bean?id='+beanId + '&im=true', function(bean) {
	    if(bean["class"]!=null){
		$('#beanClass').html('<em>' + beanId + '</em>: ' + bean["class"].substr(bean["class"].lastIndexOf('.')+1));
	    }else{
		$('#beanClass').text(beanId)
	    }

	    var $bf=$('#beanFields').empty();
	    $.each(bean["properties"], function(key, val){
		$bf.append($('<li></li>').addClass('list-group-item').html('<em>' + key + '</em> = ' + val));
	    });

	    var $bm=$('#beanMethods').empty();
	    $.each(bean["methods"], function(i, meth){
		$bm.append($('<li></li>').addClass('list-group-item').html(meth.returnType + ' <em>' + meth.name + '</em>' + '('  + meth.paramTypes.join(', ')+ ')'));
	    });
	    $('#infoPanel').show();
	});
    }

    function loadScript(scriptId){
	scriptStore.retrieve(scriptId, function(value){
	    cm.setValue(value);
	    $('#scriptName').val(scriptId);
	});
    }

    function saveScript(){
	var aText = cm.getValue(); //$("#scriptArea").val();
	if(aText.trim().length>0){
	    var $sn = $('#scriptName');
	    var sn = $sn.val();
	    if(sn.trim().length<=0){
		$sn.parent().addClass('has-error');
		return;
	    }
	    $sn.parent().removeClass('has-error');
	    scriptStore.saveScript(sn, aText);
	}
    }

    function firebaseStore(dataRef){
	var scriptsRef = dataRef.child('scripts');
	return {
	    saveScript:function(name, scriptBody){
		scriptsRef.child(name).set({content:scriptBody});
	    },
	    bindTo:function(elem){		
		scriptsRef.on('child_added', function(snapshot){
		    //console.log('snapshot is ' + JSON.stringify(snapshot));
		    var name = snapshot.name();
		    $(elem).append($('<option></option>').attr('value', name).text(name));
		});
	    },
	    retrieve:function(name, callback){
		scriptsRef.child(name).on('value', function(snapshot){
		    callback(snapshot.val().content);
		});
	    }
	}
    }

    function localStore(){
	return {
	    saveScript:function(name, scriptBody){
		var stored = JSON.parse(localStorage.getItem('STORED_SCRIPTS'));
		if(stored==null) stored = {};
		var isnew = typeof(stored[name])=='undefined';
		stored[name]=scriptBody;
		localStorage.setItem('STORED_SCRIPTS', JSON.stringify(stored));
		if(isnew){
		    this.selectField.append($('<option></option>').attr('value', name).text(name));
		}
	    },
	    bindTo:function(elem){
		this.selectField = $(elem);
		var stored = JSON.parse(localStorage.getItem('STORED_SCRIPTS'));
		var $ss = $(elem);
		$.each(stored, function(name, value){
		    $ss.append($('<option></option>').attr('value', name).text(name))
		});
	    },
	    retrieve: function(name,callback){
		var stored = JSON.parse(localStorage.getItem('STORED_SCRIPTS'));
		callback(stored[name]);
	    }
	};
    }

    //config firebase from data
    function initScriptStore(){
	//default to local store
	scriptStore = localStore();
	$.ajax({type:'GET', url:'/spring/firebase', contentType:'application/json', success:function(data){
	    if(data.firebaseUrl){
		console.log('connecting to firebase');
		var dataRef = new Firebase(data.firebaseUrl);
		dataRef.auth(data.firebaseJwt, function(error){
		    if(error){
			console.log('Failed authentication to firebase ' + error);
		    }else{
			scriptStore = firebaseStore(dataRef);
			console.log('connected to firebase');
		    }
		    scriptStore.bindTo('#savedScripts');			
		});
	    } else{
		console.log('firebase details not provided :' + data);
		scriptStore.bindTo('#savedScripts');
	    }
	}});
    }

    //initialise
    $('#content').height($(window).height());
    cm = CodeMirror.fromTextArea($('#scriptArea').get(0));
    cm.setSize('100%',160);
    console.log('code mirror ' + cm);
    initScriptStore();
    $('title').text('Spring Console: ' + window.location.host);

    var $bl = $('#beanList');
    $bl.empty();
    $.getJSON('/spring/beanNames', function(data) {
	beans = data;
	$.each(data, function(index, val) {
	    $bl.append($('<option></option>').attr('value',val).text(val));
	});
    });

    $bl.click(function(event){
	showBeanInfo($(event.target).val());
    });

    $('#savedScripts').change(function(e){
	loadScript($(this).val());
    });
    $('#btnExec').click(function(){
	postScript(cm.getValue());//$('#scriptArea').val());
    });
    $('#btnSave').click(function(){
	saveScript();
    });
    $('#scriptName').keypress(function(e){
	if(e.which==13) saveScript();
    });
    $('#scriptArea').parent().keydown(function(e){
	if(event.which==13 && event.ctrlKey){
	    postScript(cm.getValue());
	}
    });
    $('#scriptContainer').resizable({
	resize: function() {
	    cm.setSize($(this).width(), $(this).height());
	}
    });
    //resize to fill window
    var gap = $(window).height() - ($('.row').position().top + $('.row').height());
    if(gap>0){
	var sc = $('#scriptContainer');
	sc.height(sc.height()+gap/2);
	cm.setSize(sc.width(), sc.height());
	var rt = $('#resultText');
	rt.height(rt.height()+gap/2);
    }
    //load server info
    $.ajax({type:'GET', url:'/spring/serverinfo', contentType:'application/json', success:function(data){
	$('#appLabel').text(data.appName + ' : ' + (data.hostname || ''));
    }});

}
