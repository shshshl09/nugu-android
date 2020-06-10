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

package com.skt.nugu.sdk.agent.ext.message

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.skt.nugu.sdk.agent.ext.message.handler.GetMessageDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.handler.SendCandidatesDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.handler.SendMessageDirectiveHandler
import com.skt.nugu.sdk.agent.ext.message.payload.GetMessagePayload
import com.skt.nugu.sdk.agent.ext.message.payload.SendCandidatesPayload
import com.skt.nugu.sdk.agent.ext.message.payload.SendMessagePayload
import com.skt.nugu.sdk.agent.version.Version
import com.skt.nugu.sdk.core.interfaces.capability.CapabilityAgent
import com.skt.nugu.sdk.core.interfaces.common.NamespaceAndName
import com.skt.nugu.sdk.core.interfaces.context.*
import com.skt.nugu.sdk.core.interfaces.directive.DirectiveSequencerInterface
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MessageAgent(
    private val client: MessageClient,
    contextStateProviderRegistry: ContextStateProviderRegistry,
    contextGetter: ContextGetterInterface,
    messageSender: MessageSender,
    directiveSequencer: DirectiveSequencerInterface
) : CapabilityAgent
    , SupportedInterfaceContextProvider
    , SendCandidatesDirectiveHandler.Controller
    , SendMessageDirectiveHandler.Controller
    , GetMessageDirectiveHandler.Controller {
    companion object {
        private const val TAG = "MessageAgent"
        const val NAMESPACE = "Message"

        val VERSION = Version(1, 0)
    }

    override fun getInterfaceName(): String = NAMESPACE

    private val executor = Executors.newSingleThreadExecutor()

    init {
        contextStateProviderRegistry.setStateProvider(
            namespaceAndName,
            this
        )

        directiveSequencer.apply {
            addDirectiveHandler(
                SendCandidatesDirectiveHandler(
                    this@MessageAgent,
                    messageSender,
                    contextGetter
                )
            )
            addDirectiveHandler(
                SendMessageDirectiveHandler(
                    this@MessageAgent,
                    messageSender,
                    contextGetter
                )
            )
            addDirectiveHandler(
                GetMessageDirectiveHandler(
                    this@MessageAgent,
                    messageSender,
                    contextGetter
                )
            )
        }
    }

    internal data class StateContext(private val context: Context): ContextState {
        companion object {
            private fun buildCompactContext(): JsonObject = JsonObject().apply {
                addProperty("version", VERSION.toString())
            }

            private val COMPACT_STATE: String = buildCompactContext().toString()
        }

        override fun toFullJsonString(): String = buildCompactContext().apply {
            context.template?.let {template->
                add("template", template.toJson())
            }
        }.toString()

        override fun toCompactJsonString(): String = COMPACT_STATE
    }

    override fun provideState(
        contextSetter: ContextSetterInterface,
        namespaceAndName: NamespaceAndName,
        stateRequestToken: Int
    ) {
        executor.submit {
            contextSetter.setState(namespaceAndName, StateContext(client.getContext()), StateRefreshPolicy.ALWAYS, stateRequestToken)
        }
    }

    override fun getCandidateList(payload: SendCandidatesPayload): List<Contact>? {
        return executor.submit(Callable {
            client.getCandidateList(payload)
        }).get()
    }

    override fun sendMessage(payload: SendMessagePayload, callback: EventCallback) {
        executor.submit {
            client.sendMessage(payload, callback)
        }
    }

    override fun getMessageList(
        payload: GetMessagePayload,
        callback: GetMessageDirectiveHandler.Callback
    ) {
        executor.submit {
            client.getMessageList(payload, callback)
        }
    }
}