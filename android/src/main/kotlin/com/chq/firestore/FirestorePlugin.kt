package com.chq.firestore

import android.util.SparseArray
import com.google.firebase.firestore.*
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.ArrayList
import java.util.HashMap

class FirestorePlugin internal constructor(private val channel: MethodChannel) : MethodCallHandler {
    private var nextHandle = 0
    private val observers = SparseArray<EventObserver>()
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
                val observer = EventObserver(handle)
                observers.put(handle, observer)
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
            "Query#removeListener" -> {
                val arguments = call.arguments<Map<String, Any>>()
                // TODO(arthurthompson): find out why removeListener is sometimes called without handle.
                val handle = arguments["handle"] as Int
                listenerRegistrations.get(handle).remove()
                listenerRegistrations.remove(handle)
                observers.remove(handle)
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


    private inner class EventObserver internal constructor(private val handle: Int) : EventListener<QuerySnapshot> {

        override fun onEvent(querySnapshot: QuerySnapshot, e: FirebaseFirestoreException?) {
            val arguments = HashMap<String, Any>()
            arguments.put("handle", handle)

            val documents = querySnapshot.documents.map { it.data }
            arguments.put("documents", documents)

            val documentChanges = ArrayList<Map<String, Any>>()
            for (documentChange in querySnapshot.documentChanges) {
                val change = HashMap<String, Any>()
                val type = when (documentChange.type) {
                    DocumentChange.Type.ADDED -> "DocumentChangeType.added"
                    DocumentChange.Type.MODIFIED -> "DocumentChangeType.modified"
                    DocumentChange.Type.REMOVED -> "DocumentChangeType.removed"
                }
                change.put("type", type)
                change.put("oldIndex", documentChange.oldIndex)
                change.put("newIndex", documentChange.newIndex)
                //        DocumentSnapshot doc = documentChange.getDocument();
                //        Map<String, Object> docData = doc.getData();
                //        docData.put("key", doc.getId());
                change.put("document", documentChange.document.data)
                documentChanges.add(change)
            }
            arguments.put("documentChanges", documentChanges)

            channel.invokeMethod("QuerySnapshot", arguments)
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


