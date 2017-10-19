package com.chq.firestore

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.PluginRegistry.Registrar

import com.google.firebase.firestore.Query

class FirestorePlugin() : MethodCallHandler {
    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar): Unit {
            val channel = MethodChannel(registrar.messenger(), "firestore")
            channel.setMethodCallHandler(FirestorePlugin())
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result): Unit {
        when (call.method) {
            "DocumentReference#setData" -> {
                val arguments = call.arguments<Map<String, Any>>()
                val documentReference = getDocumentReference(arguments)
                val data = arguments["data"] as Any
                documentReference.set(data)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun getQuery(arguments: Map<String, Any>): Query = getCollectionReference(arguments)

    private fun getCollectionReference(arguments: Map<String, Any>): CollectionReference {
        val path = arguments["path"] as String
        return FirebaseFirestore.getInstance().collection(path)
    }

    private fun getDocumentReference(arguments: Map<String, Any>): DocumentReference {
        val path = arguments["path"] as String
        return FirebaseFirestore.getInstance().document(path)
    }
}
