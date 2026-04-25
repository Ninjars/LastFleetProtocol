package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.entities

sealed interface ShipBuilderSideEffect {
    data object NavigateBack : ShipBuilderSideEffect

    /**
     * Display a transient message in the screen-root snackbar. Used by the asset-export
     * flow to surface success / overwrite / error / bundle-collision wording without
     * leaking the full path through the side-effect channel as a format token — the VM
     * has already done the substitution via `stringResource(...)` before emitting.
     */
    data class ShowToast(val text: String) : ShipBuilderSideEffect

    /**
     * Put [text] on the system clipboard, then surface [toastMessage] in the snackbar so
     * the dev knows the copy happened and where to paste it. Used when the export gate
     * is closed (no repo-root resolved) and the dev needs to manually paste the JSON
     * into a new file.
     */
    data class CopyToClipboard(
        val text: String,
        val toastMessage: String,
    ) : ShipBuilderSideEffect
}
