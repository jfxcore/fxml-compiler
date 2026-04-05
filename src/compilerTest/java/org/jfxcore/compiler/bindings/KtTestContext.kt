//@file:Suppress("unused")
//
//package org.jfxcore.compiler.bindings
//
//import javafx.beans.property.ktx.BooleanPropertyDelegate
//import javafx.beans.property.ktx.DoublePropertyDelegate
//import javafx.beans.property.ktx.ObjectPropertyDelegate
//
//class KtTestContext0 {
//    var boolProp: Boolean by BooleanPropertyDelegate()
//}
//
//class KtTestContext1 {
//    var readOnlyDoubleProp: Double by DoublePropertyDelegate(123.0)
//        private set
//
//    fun changeValue(value: Double) {
//        readOnlyDoubleProp = value
//    }
//}
//
//class KtTestContext2 {
//    private var privateBoolProp: Boolean by BooleanPropertyDelegate()
//}
//
//class KtTestContext3 {
//    val readOnlyBoolProp: Boolean by BooleanPropertyDelegate(true)
//}
//
//class KtTestContext4 {
//    var data by ObjectPropertyDelegate("data")
//}
//
//class DataHolder<T>(var value: T)
//
//class KtTestContext5 {
//    var data by ObjectPropertyDelegate(DataHolder("data"))
//}