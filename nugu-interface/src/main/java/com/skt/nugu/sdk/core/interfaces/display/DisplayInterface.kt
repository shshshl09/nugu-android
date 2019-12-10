package com.skt.nugu.sdk.core.interfaces.display

import com.skt.nugu.sdk.core.interfaces.common.EventCallback

/**
 * The basic interface for display
 */
interface DisplayInterface<Renderer> {
    /**
     * enum class for ErrorType
     */
    enum class ErrorType {
        REQUEST_FAIL,
        RESPONSE_TIMEOUT
    }

    /**
     * callback interface for [setElementSelected]
     */
    interface OnElementSelectedCallback : EventCallback<ErrorType>

    /**
     * Each element has it's own token.
     *
     * This should be called when element selected(clicked) by the renderer.
     *
     * @param templateId the unique identifier for the template card
     * @param token the unique identifier for the element
     * @param callback the result callback for element selected event
     */
    fun setElementSelected(templateId: String, token: String, callback: OnElementSelectedCallback?)

    /**
     * Notifies the display that has been rendered.
     *
     * This should be called when the display rendered by the renderer.
     *
     * @param templateId the templateId that has been rendered
     */
    fun displayCardRendered(templateId: String)

    /**
     * Notifies the display that has been cleared.
     *
     * This should be called when the display cleared by the renderer.
     *
     * @param templateId the templateId that has been cleared
     */
    fun displayCardCleared(templateId: String)

    /** Set a renderer to interact with the display agent.
     * @param renderer the renderer to be set
     */
    fun setRenderer(renderer: Renderer?)

    /**
     * Stop rendering timer for [templateId].
     * The SDK manage a timer to notify proper display clear timing.
     * Call this API to stop timer, if client do not want the behavior.
     * @param templateId the templateId associated with internal timer
     */
    fun stopRenderingTimer(templateId: String)
}