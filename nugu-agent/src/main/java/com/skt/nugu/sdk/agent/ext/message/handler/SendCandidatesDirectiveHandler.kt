/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
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

package com.skt.nugu.sdk.agent.ext.message.handler

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.AbstractDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.Contact
import com.skt.nugu.sdk.agent.ext.message.MessageAgent
import com.skt.nugu.sdk.agent.ext.message.payload.SendCandidatesPayload
import com.skt.nugu.sdk.agent.util.IgnoreErrorContextRequestor
import com.skt.nugu.sdk.agent.util.MessageFactory
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.ContextGetterInterface
import com.skt.nugu.sdk.core.interfaces.directive.BlockingPolicy
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest

class SendCandidatesDirectiveHandler(
    private val controller: Controller,
    private val messageSender: MessageSender,
    private val contextGetter: ContextGetterInterface
) : AbstractDirectiveHandler() {
    companion object {
        private const val NAME_SEND_CANDIDATES = "SendCandidates"
        private const val NAME_CANDIDATES_LISTED = "CandidatesListed"

        private val SEND_CANDIDATES = NamespaceAndName(MessageAgent.NAMESPACE, NAME_SEND_CANDIDATES)
    }

    interface Controller {
        fun getCandidateList(payload: SendCandidatesPayload): List<Contact>?
    }

    override fun preHandleDirective(info: DirectiveInfo) {
    }

    override fun handleDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())

        val payload =
            MessageFactory.create(info.directive.payload, SendCandidatesPayload::class.java)
        if (payload == null) {
            info.result.setFailed("Invalid Payload")
        } else {
            info.result.setCompleted()
            val candidates = controller.getCandidateList(payload)
            if (candidates != null) {
                contextGetter.getContext(object : IgnoreErrorContextRequestor() {
                    override fun onContext(jsonContext: String) {
                        messageSender.sendMessage(
                            EventMessageRequest.Builder(
                                jsonContext,
                                MessageAgent.NAMESPACE,
                                NAME_CANDIDATES_LISTED,
                                MessageAgent.VERSION.toString()
                            ).payload(JsonObject().apply {
                                addProperty("playServiceId", payload.playServiceId)
                            }.toString())
                                .referrerDialogRequestId(info.directive.getDialogRequestId())
                                .build()
                        )
                    }
                })
            }
        }
    }

    override fun cancelDirective(info: DirectiveInfo) {
        removeDirective(info.directive.getMessageId())
    }

    override fun getConfiguration(): Map<NamespaceAndName, BlockingPolicy> {
        val configurations = HashMap<NamespaceAndName, BlockingPolicy>()

        configurations[SEND_CANDIDATES] = BlockingPolicy()

        return configurations
    }
}