goog.provide('firelisp.rules');
goog.require('cljs.core');
goog.require('firelisp.paths');
goog.require('firelisp.convert');
goog.require('firelisp.template');
firelisp.rules.rulefn_STAR_ = (function firelisp$rules$rulefn_STAR_(var_args){
var args__3832__auto__ = [];
var len__3829__auto___1263 = arguments.length;
var i__3830__auto___1264 = (0);
while(true){
if((i__3830__auto___1264 < len__3829__auto___1263)){
args__3832__auto__.push((arguments[i__3830__auto___1264]));

var G__1265 = (i__3830__auto___1264 + (1));
i__3830__auto___1264 = G__1265;
continue;
} else {
}
break;
}

var argseq__3833__auto__ = ((((2) < args__3832__auto__.length))?(new cljs.core.IndexedSeq(args__3832__auto__.slice((2)),(0),null)):null);
return firelisp.rules.rulefn_STAR_.cljs$core$IFn$_invoke$arity$variadic((arguments[(0)]),(arguments[(1)]),argseq__3833__auto__);
});

firelisp.rules.rulefn_STAR_.cljs$core$IFn$_invoke$arity$variadic = (function (_AMPERSAND_form,_AMPERSAND_env,body){
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.common","with-template-quotes","firelisp.common/with-template-quotes",1501440452,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"fn","fn",465265323,null)], null),body))], null)));
});

firelisp.rules.rulefn_STAR_.cljs$lang$maxFixedArity = (2);

firelisp.rules.rulefn_STAR_.cljs$lang$applyTo = (function (seq1260){
var G__1261 = cljs.core.first.call(null,seq1260);
var seq1260__$1 = cljs.core.next.call(null,seq1260);
var G__1262 = cljs.core.first.call(null,seq1260__$1);
var seq1260__$2 = cljs.core.next.call(null,seq1260__$1);
return firelisp.rules.rulefn_STAR_.cljs$core$IFn$_invoke$arity$variadic(G__1261,G__1262,seq1260__$2);
});


firelisp.rules.rulefn_STAR_.cljs$lang$macro = true;
firelisp.rules.rulefn = (function firelisp$rules$rulefn(var_args){
var args__3832__auto__ = [];
var len__3829__auto___1271 = arguments.length;
var i__3830__auto___1272 = (0);
while(true){
if((i__3830__auto___1272 < len__3829__auto___1271)){
args__3832__auto__.push((arguments[i__3830__auto___1272]));

var G__1273 = (i__3830__auto___1272 + (1));
i__3830__auto___1272 = G__1273;
continue;
} else {
}
break;
}

var argseq__3833__auto__ = ((((3) < args__3832__auto__.length))?(new cljs.core.IndexedSeq(args__3832__auto__.slice((3)),(0),null)):null);
return firelisp.rules.rulefn.cljs$core$IFn$_invoke$arity$variadic((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),argseq__3833__auto__);
});

firelisp.rules.rulefn.cljs$core$IFn$_invoke$arity$variadic = (function (_AMPERSAND_form,_AMPERSAND_env,name,body){
var body__$1 = (function (){var G__1270 = body;
if(typeof cljs.core.first.call(null,body) === 'string'){
return cljs.core.rest.call(null,G__1270);
} else {
return G__1270;
}
})();
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"swap!","swap!",-655677516,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.compile","*rule-fns*","firelisp.compile/*rule-fns*",-718677445,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"assoc","assoc",2071440380,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"quote","quote",1377916282,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [name], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","rulefn*","firelisp.rules/rulefn*",661155560,null)], null),cljs.core.cons.call(null,name,body__$1)))], null)));
});

firelisp.rules.rulefn.cljs$lang$maxFixedArity = (3);

firelisp.rules.rulefn.cljs$lang$applyTo = (function (seq1266){
var G__1267 = cljs.core.first.call(null,seq1266);
var seq1266__$1 = cljs.core.next.call(null,seq1266);
var G__1268 = cljs.core.first.call(null,seq1266__$1);
var seq1266__$2 = cljs.core.next.call(null,seq1266__$1);
var G__1269 = cljs.core.first.call(null,seq1266__$2);
var seq1266__$3 = cljs.core.next.call(null,seq1266__$2);
return firelisp.rules.rulefn.cljs$core$IFn$_invoke$arity$variadic(G__1267,G__1268,G__1269,seq1266__$3);
});


firelisp.rules.rulefn.cljs$lang$macro = true;
firelisp.rules.blank_rules = new cljs.core.PersistentArrayMap(null, 8, [new cljs.core.Keyword(null,"read","read",1140058661),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"create","create",-1301499256),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"update","update",1045576396),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"delete","delete",-1768633620),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"validate","validate",-201300827),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"write","write",-1857649168),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"index","index",-1531685915),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"children","children",-940561982),cljs.core.PersistentHashSet.EMPTY], null);
firelisp.rules.authorize = (function firelisp$rules$authorize(_AMPERSAND_form,_AMPERSAND_env,m){
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"do","do",1686842252,null)], null),(function (){var iter__3648__auto__ = (function firelisp$rules$authorize_$_iter__1294(s__1295){
return (new cljs.core.LazySeq(null,(function (){
var s__1295__$1 = s__1295;
while(true){
var temp__4657__auto__ = cljs.core.seq.call(null,s__1295__$1);
if(temp__4657__auto__){
var s__1295__$2 = temp__4657__auto__;
if(cljs.core.chunked_seq_QMARK_.call(null,s__1295__$2)){
var c__3646__auto__ = cljs.core.chunk_first.call(null,s__1295__$2);
var size__3647__auto__ = cljs.core.count.call(null,c__3646__auto__);
var b__1297 = cljs.core.chunk_buffer.call(null,size__3647__auto__);
if((function (){var i__1296 = (0);
while(true){
if((i__1296 < size__3647__auto__)){
var vec__1306 = cljs.core._nth.call(null,c__3646__auto__,i__1296);
var type = cljs.core.nth.call(null,vec__1306,(0),null);
var rule = cljs.core.nth.call(null,vec__1306,(1),null);
cljs.core.chunk_append.call(null,b__1297,(function (){var G__1309 = (((type instanceof cljs.core.Keyword))?type.fqn:null);
switch (G__1309) {
case "validate":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","validate","firelisp.rules/validate",1429054789,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "create":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "read":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "update":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "delete":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "index":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "write":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "children":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
default:
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","at","firelisp.rules/at",-1183691051,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

}
})());

var G__1315 = (i__1296 + (1));
i__1296 = G__1315;
continue;
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__1297),firelisp$rules$authorize_$_iter__1294.call(null,cljs.core.chunk_rest.call(null,s__1295__$2)));
} else {
return cljs.core.chunk_cons.call(null,cljs.core.chunk.call(null,b__1297),null);
}
} else {
var vec__1310 = cljs.core.first.call(null,s__1295__$2);
var type = cljs.core.nth.call(null,vec__1310,(0),null);
var rule = cljs.core.nth.call(null,vec__1310,(1),null);
return cljs.core.cons.call(null,(function (){var G__1313 = (((type instanceof cljs.core.Keyword))?type.fqn:null);
switch (G__1313) {
case "validate":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","validate","firelisp.rules/validate",1429054789,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "create":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "read":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "update":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "delete":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "index":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "write":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
case "children":
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","add","firelisp.rules/add",1868812013,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

break;
default:
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","at","firelisp.rules/at",-1183691051,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [type], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [rule], null)));

}
})(),firelisp$rules$authorize_$_iter__1294.call(null,cljs.core.rest.call(null,s__1295__$2)));
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__3648__auto__.call(null,cljs.core.seq.call(null,m));
})()));
});

firelisp.rules.authorize.cljs$lang$macro = true;
firelisp.rules.at = (function firelisp$rules$at(var_args){
var args__3832__auto__ = [];
var len__3829__auto___1328 = arguments.length;
var i__3830__auto___1329 = (0);
while(true){
if((i__3830__auto___1329 < len__3829__auto___1328)){
args__3832__auto__.push((arguments[i__3830__auto___1329]));

var G__1330 = (i__3830__auto___1329 + (1));
i__3830__auto___1329 = G__1330;
continue;
} else {
}
break;
}

var argseq__3833__auto__ = ((((3) < args__3832__auto__.length))?(new cljs.core.IndexedSeq(args__3832__auto__.slice((3)),(0),null)):null);
return firelisp.rules.at.cljs$core$IFn$_invoke$arity$variadic((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),argseq__3833__auto__);
});

firelisp.rules.at.cljs$core$IFn$_invoke$arity$variadic = (function (_AMPERSAND_form,_AMPERSAND_env,path,body){
var body__$1 = (function (){var G__1322 = body;
if(cljs.core.map_QMARK_.call(null,cljs.core.first.call(null,body))){
return cljs.core.cons.call(null,cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","authorize","firelisp.rules/authorize",-1058373225,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.first.call(null,body)], null))),cljs.core.drop.call(null,(1),G__1322));
} else {
return G__1322;
}
})();
return cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"try","try",-1273693247,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"let","let",358118826,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.vec.call(null,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"segments__auto__1323","segments__auto__1323",459400859,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"map","map",-1282745308,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"str","str",-1564826950,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.paths","parse-path","firelisp.paths/parse-path",1005370142,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [path], null)))], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"leaf-rules__auto__1324","leaf-rules__auto__1324",-127546641,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"binding","binding",-2114503176,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.vec.call(null,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.compile","*path*","firelisp.compile/*path*",869295884,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"apply","apply",-1334050276,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"conj","conj",-1127293942,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.compile","*path*","firelisp.compile/*path*",869295884,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"segments__auto__1323","segments__auto__1323",459400859,null)], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","*rules*","firelisp.rules/*rules*",-855900698,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"atom","atom",1243487874,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [firelisp.rules.blank_rules], null)))], null)))], null),firelisp.convert.convert_quotes.call(null,body__$1),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("clojure.core","deref","clojure.core/deref",188719157,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","*rules*","firelisp.rules/*rules*",-855900698,null)], null)))], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"rules__auto__1325","rules__auto__1325",197879200,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"->>","->>",-1874332161,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"leaf-rules__auto__1324","leaf-rules__auto__1324",-127546641,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","update-in-map","firelisp.rules/update-in-map",77729129,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"some->","some->",-1011172200,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","*rules*","firelisp.rules/*rules*",-855900698,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"deref","deref",1494944732,null)], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"segments__auto__1323","segments__auto__1323",459400859,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","merge-rules","firelisp.rules/merge-rules",1431130877,null)], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","filter-by-value","firelisp.rules/filter-by-value",6563554,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"fn*","fn*",-752876845,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.vec.call(null,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"p1__1317__auto__1326","p1__1317__auto__1326",-801871143,null)], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"not","not",1044554643,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"and","and",668631710,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"set?","set?",1636014792,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"p1__1317__auto__1326","p1__1317__auto__1326",-801871143,null)], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"empty?","empty?",76408555,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"p1__1317__auto__1326","p1__1317__auto__1326",-801871143,null)], null)))], null)))], null)))], null)))], null)))], null)))], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"some->","some->",-1011172200,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","*rules*","firelisp.rules/*rules*",-855900698,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"swap!","swap!",-655677516,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("firelisp.rules","merge-rules","firelisp.rules/merge-rules",1431130877,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"rules__auto__1325","rules__auto__1325",197879200,null)], null)))], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"rules__auto__1325","rules__auto__1325",197879200,null)], null)))], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"catch","catch",-1616370245,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("js","Error","js/Error",-1692659266,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"e__auto__1327","e__auto__1327",-484878063,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.apply.call(null,cljs.core.list,cljs.core.concat.call(null,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,".log",".log",565247729,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol("js","console","js/console",-1426368245,null)], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, ["at error"], null),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"e__auto__1327","e__auto__1327",-484878063,null)], null)))], null)))], null)));
});

firelisp.rules.at.cljs$lang$maxFixedArity = (3);

firelisp.rules.at.cljs$lang$applyTo = (function (seq1318){
var G__1319 = cljs.core.first.call(null,seq1318);
var seq1318__$1 = cljs.core.next.call(null,seq1318);
var G__1320 = cljs.core.first.call(null,seq1318__$1);
var seq1318__$2 = cljs.core.next.call(null,seq1318__$1);
var G__1321 = cljs.core.first.call(null,seq1318__$2);
var seq1318__$3 = cljs.core.next.call(null,seq1318__$2);
return firelisp.rules.at.cljs$core$IFn$_invoke$arity$variadic(G__1319,G__1320,G__1321,seq1318__$3);
});


firelisp.rules.at.cljs$lang$macro = true;
