package com.chq.firestore

import android.util.SparseArray
import com.google.firebase.firestore.*
import com.google.firebase.firestore.EventListener
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.*

class FirestorePlugin internal constructor(private val channel: MethodChannel) : MethodCallHandler {
    private var nextHandle = 0
    private val queryObservers = SparseArray<QueryObserver>()
    private val documentObservers = SparseArray<DocumentObserver>()
    private val listenerRegistrations = SparseArray<ListenerRegistration>()


    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar): Unit {
            val channel = MethodChannel(registrar.messenger(), "firestore")
            channel.setMethodCallHandler(FirestorePlugin(channel))
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
            "Query#addSnapshotListener" -> {
                val arguments = call.arguments<Map<String, Any>>()

                val handle = nextHandle++
                val observer = QueryObserver(handle)
                queryObservers.put(handle, observer)
                listenerRegistrations.put(handle, getQuery(arguments).addSnapshotListener(observer))
                result.success(handle)
            }
            "Query#addDocumentListener" -> {
                val arguments = call.arguments<Map<String, Any>>()
                val handle = nextHandle++
                val observer = DocumentObserver(handle)
                documentObservers.put(handle, observer)
                listenerRegistrations.put(
                        handle, getDocumentReference(arguments).addSnapshotListener(observer))
                result.success(handle)
            }
            "Query#removeQueryListener" -> {
                val arguments = call.arguments<Map<String, Any>>()
                val handle = arguments["handle"] as Int
                listenerRegistrations.get(handle).remove()
                listenerRegistrations.remove(handle)
                queryObservers.remove(handle)
                result.success(null)
            }
            "Query#removeDocumentListener" -> {
                val arguments = call.arguments<Map<String, Any>>()
                val handle = arguments["handle"] as Int
                listenerRegistrations.get(handle).remove()
                listenerRegistrations.remove(handle)
                documentObservers.remove(handle)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private inner class DocumentObserver internal constructor(private val handle: Int) : EventListener<DocumentSnapshot> {
        override fun onEvent(documentSnapshot: DocumentSnapshot, e: FirebaseFirestoreException?) {
            val arguments = HashMap<String, Any>()
            arguments.put("handle", handle)
            if (documentSnapshot.exists()) {
                arguments["data"] = documentSnapshot.data;
            }
            channel.invokeMethod("DocumentSnapshot", arguments)
        }
    }


    private inner class QueryObserver internal constructor(private val handle: Int) : EventListener<QuerySnapshot> {

        override fun onEvent(querySnapshot: QuerySnapshot, e: FirebaseFirestoreException?) {
            val arguments = HashMap<String, Any>()
            arguments.put("handle", handle)

            val documents = querySnapshot.documents.map(::documentSnapshotToMap)
            arguments.put("documents", documents)

            val documentChanges = ArrayList<Map<String, Any>>()
            for (documentChange in querySnapshot.documentChanges) {
                val change = HashMap<String, Any>()
                change.put("type", documentChange.type.ordinal)
                change.put("oldIndex", documentChange.oldIndex)
                change.put("newIndex", documentChange.newIndex)
                change.put("document", documentSnapshotToMap(documentChange.document))
                documentChanges.add(change)
            }
            arguments.put("documentChanges", documentChanges)

            channel.invokeMethod("QuerySnapshot", arguments)
        }
    }

    private fun getQuery(arguments: Map<String, Any>): Query {
        val parameters = arguments["parameters"] as Map<*, *>?

        val limit = parameters?.get("limit") as? Int
        val orderBy = parameters?.get("orderBy") as? String
        val descending = parameters?.get("descending") as? Boolean
        val startAt = parameters?.get("startAt") as? String
        val endAt = parameters?.get("endAt") as? String

        var query: Query = getCollectionReference(arguments)

        if (limit != null) query = query.limit(limit.toLong())
        if (orderBy != null && descending != null) query = query.orderBy(orderBy, if (descending) Query.Direction.DESCENDING else Query.Direction.ASCENDING)
        if (orderBy != null && descending == null) query = query.orderBy(orderBy)
        if (startAt != null) query = query.startAt(startAt)
        if (endAt != null) query = query.endAt(endAt)

        return query
    }

    private fun getCollectionReference(arguments: Map<String, Any>): CollectionReference {
        val path = arguments["path"] as String
        return FirebaseFirestore.getInstance().collection(path)
    }

    private fun getDocumentReference(arguments: Map<String, Any>): DocumentReference {
        val path = arguments["path"] as String
        return FirebaseFirestore.getInstance().document(path)
    }
}
