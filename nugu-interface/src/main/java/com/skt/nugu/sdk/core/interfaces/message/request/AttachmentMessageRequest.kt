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
package com.skt.nugu.sdk.core.interfaces.message.request

import com.skt.nugu.sdk.core.interfaces.message.MessageRequest

/**
 * Class for requesting a attachment message
 * @param dialogRequestId the id for request that generated by client.
 * @param messageId the unique id for the message
 * @param context the context for the message
 * @param name the name of directive
 * @param namespace the namespace of directive
 * @param version the version
 * @param seq the sequence
 * @param isEnd end of message
 * @param parentMessageId the parent message id
 * @param mediaType the media type
 * @param byteArray the byteArray is data
 */
data class AttachmentMessageRequest(
    val messageId: String,
    val dialogRequestId: String,
    val context: String,
    val namespace: String,
    val name: String,
    val version: String,
    val referrerDialogRequestId: String,
    val seq: Int,
    val isEnd: Boolean,
    val parentMessageId: String,
    val mediaType: String,
    val byteArray: ByteArray?
) : MessageRequest