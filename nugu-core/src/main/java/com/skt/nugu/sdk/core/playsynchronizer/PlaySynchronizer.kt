/**
 * Copyright (c) 2019 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.core.playsynchronizer

import com.skt.nugu.sdk.core.interfaces.playsynchronizer.PlaySynchronizerInterface
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PlaySynchronizer : PlaySynchronizerInterface {
    companion object {
        private const val TAG = "PlaySynchronizer"
    }

    private val lock = ReentrantLock()

    /**
     * waiting start sync object : the sync object which is prepared
     * should release sync object : the sync object which is started
     */
    private data class ContextInfo(
        private val prepared: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet(),
        private val started: HashSet<PlaySynchronizerInterface.SynchronizeObject> = HashSet()
    ) {
        fun prepare(obj: PlaySynchronizerInterface.SynchronizeObject) {
            if(prepared.add(obj)) {
                notifyOnStateChanged(obj.getDialogRequestId(), obj.getPlayServiceId())
            }
        }

        fun start(syncObj: PlaySynchronizerInterface.SynchronizeObject): Boolean {
            return if(prepared.remove(syncObj)) {
                started.add(syncObj)
                notifyOnStateChanged(syncObj.getDialogRequestId(), syncObj.getPlayServiceId())
                true
            } else {
                false
            }
        }

        fun cancel(syncObj: PlaySynchronizerInterface.SynchronizeObject): Boolean {
            val preparedRemoved = prepared.remove(syncObj)
            val startedRemoved = started.remove(syncObj)

            return if(preparedRemoved || startedRemoved) {
                cancel(syncObj.getDialogRequestId())
                true
            } else {
                false
            }
        }

        fun cancel(dialogRequestId: String) {
            prepared.filter {
                it.getDialogRequestId() == dialogRequestId
            }.forEach {
                it.requestReleaseSync()
            }

            started.filter {
                it.getDialogRequestId() == dialogRequestId
            }.forEach {
                it.requestReleaseSync()
            }
        }

        fun finish(syncObj: PlaySynchronizerInterface.SynchronizeObject): Boolean {
            val preparedRemoved = prepared.remove(syncObj)
            val startedRemoved = started.remove(syncObj)

            if(preparedRemoved || startedRemoved) {
                notifyOnStateChanged(syncObj.getDialogRequestId(), syncObj.getPlayServiceId())
            }

            return preparedRemoved || startedRemoved
        }

        private fun notifyOnStateChanged(dialogRequestId: String, playServiceId: String?) {
            filterContext(dialogRequestId, playServiceId).apply {
                first.forEach {
                    it.onSyncStateChanged(first, second)
                }

                second.forEach {
                    it.onSyncStateChanged(first, second)
                }
            }
        }

        private fun filterContext(dialogRequestId: String, playServiceId: String?): Pair<List<PlaySynchronizerInterface.SynchronizeObject>, List<PlaySynchronizerInterface.SynchronizeObject>> {
            val filteredPrepared = prepared.filter() {
                if(playServiceId.isNullOrBlank()) {
                    it.getDialogRequestId() == dialogRequestId
                } else {
                    it.getPlayServiceId() == playServiceId || it.getDialogRequestId() == dialogRequestId
                }
            }

            val filteredStarted = started.filter {
                if(playServiceId.isNullOrBlank()) {
                    it.getDialogRequestId() == dialogRequestId
                } else {
                    it.getPlayServiceId() == playServiceId || it.getDialogRequestId() == dialogRequestId
                }
            }

            return Pair(filteredPrepared, filteredStarted)
        }
    }

    private val syncContext = ContextInfo()

    override fun prepareSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
        lock.withLock {
            Logger.d(
                TAG,
                "[prepareSync] dialogRequestId: ${synchronizeObject.getDialogRequestId()}, synchronizeObject: $synchronizeObject"
            )

            syncContext.prepare(synchronizeObject)

            Logger.d(TAG, "[prepareSync] syncContext: $syncContext")
        }
    }

    override fun startSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?
    ) {
        lock.withLock {
            val dialogRequestId = synchronizeObject.getDialogRequestId()
            Logger.d(TAG, "[startSync] dialogRequestId: $dialogRequestId, listener: $listener")

            if(syncContext.start(synchronizeObject)) {
                listener?.onGranted()
                Logger.d(TAG, "[startSync] granted.")
            } else {
                listener?.onDenied()
                Logger.w(TAG, "[startSync] denied: prepareSync not called.")
            }
        }
    }

    override fun releaseSync(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?
    ) {
        releaseSyncInternal(synchronizeObject, listener, false)
    }

    override fun releaseSyncImmediately(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?
    ) {
        releaseSyncInternal(synchronizeObject, listener, true)
    }

//    override fun releaseWithoutSync(synchronizeObject: PlaySynchronizerInterface.SynchronizeObject) {
//        lock.withLock {
//            syncContext.finish(synchronizeObject, false)
//            Logger.d(TAG, "[releaseWithoutSync] syncContext: $syncContext")
//        }
//    }

    private fun releaseSyncInternal(
        synchronizeObject: PlaySynchronizerInterface.SynchronizeObject,
        listener: PlaySynchronizerInterface.OnRequestSyncListener?,
        immediate: Boolean
    ) {
        lock.withLock {
            Logger.d(
                TAG,
                "[releaseSyncInternal] dialogRequestId: ${synchronizeObject.getDialogRequestId()} ,object: $synchronizeObject, listener: $listener, immediate: $immediate"
            )

            val released = if(immediate) {
                syncContext.cancel(synchronizeObject)
            } else {
                syncContext.finish(synchronizeObject)
            }

            if(released) {
                listener?.onGranted()
            } else {
                listener?.onDenied()
            }

            Logger.d(TAG, "[releaseSyncInternal] syncContext: $syncContext")
        }
    }

    override fun cancelSync(dialogRequestId: String) {
        lock.withLock {
            Logger.d(TAG, "[cancelSync] dialogRequestId: $dialogRequestId")
            syncContext.cancel(dialogRequestId)
        }
    }
}
