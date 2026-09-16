package com.sympauthy.business.manager.flow.confirm

import com.sympauthy.business.manager.flow.InteractiveFlowPurposeHandler
import com.sympauthy.business.model.flow.InteractiveFlowPurpose
import com.sympauthy.business.model.flow.InteractiveFlowSession
import com.sympauthy.business.model.flow.InteractiveFlowStep
import com.sympauthy.business.model.flow.OnGoingInteractiveFlowSession
import com.sympauthy.business.model.flow.PurposeDebugInformation
import com.sympauthy.util.wireName
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * [InteractiveFlowPurposeHandler] for [InteractiveFlowPurpose.CONFIRM].
 *
 * Gates the rest of the session on the signed-in end-user explicitly approving the action a client (or an
 * administrator) initiated on their behalf. While not approved, it presents the confirmation step
 * ([InteractiveFlowStep.Confirm]); the purpose resolves once the session's confirm record is marked confirmed
 * (the persisted [com.sympauthy.business.model.flow.InteractiveFlowSessionConfirm.confirmed] marker).
 *
 * The purpose has no terminal effect: the approval is recorded during its step, so the engine simply marks it
 * complete once resolved. Cancellation (denial) is an explicit flow-API action that marks the session
 * cancelled, handled outside this handler.
 */
@Singleton
class ConfirmInteractiveFlowPurposeHandler(
    @Inject private val confirmManager: InteractiveFlowSessionConfirmManager,
) : InteractiveFlowPurposeHandler {

    override val purpose = InteractiveFlowPurpose.CONFIRM

    override suspend fun nextStepOrNull(session: OnGoingInteractiveFlowSession): InteractiveFlowStep? {
        val confirmed = confirmManager.fetchConfirmOrNull(session)?.confirmed ?: false
        return if (confirmed) null else InteractiveFlowStep.Confirm
    }

    /**
     * What the end-user is being asked to approve, who asked, and whether they have.
     *
     * A confirm record naming no client was initiated by an administrator, which the value says in words: a
     * label with no value beside it would read as a record that is missing rather than as one that is there
     * and names nobody.
     */
    override suspend fun debugInformation(session: InteractiveFlowSession): List<PurposeDebugInformation> {
        val confirm = confirmManager.fetchConfirmOrNull(session)
        return listOf(
            PurposeDebugInformation("Action", confirm?.action?.wireName),
            PurposeDebugInformation("Initiated by", confirm?.let { it.clientId ?: BY_AN_ADMINISTRATOR }),
            PurposeDebugInformation("Confirmed date", confirm?.confirmedDate?.toString())
        )
    }

    companion object {
        /** What the confirm record's null client id means, written for the operator reading it. */
        private const val BY_AN_ADMINISTRATOR = "an administrator"
    }
}
