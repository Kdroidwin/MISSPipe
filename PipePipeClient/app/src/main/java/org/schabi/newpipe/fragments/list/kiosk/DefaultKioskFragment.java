package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

public class DefaultKioskFragment extends KioskFragment {
    private int selectedServiceId = -1;
    private boolean hasAvailableKiosk = true;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (serviceId < 0) {
            updateSelectedDefaultKiosk();
        }
    }

    @Override
    public void onResume() {
        final boolean shouldResolve = selectedServiceId
                != ServiceHelper.getSelectedServiceId(requireContext()) || !hasAvailableKiosk;
        if (shouldResolve) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            updateSelectedDefaultKiosk();
        }

        super.onResume();

        if (shouldResolve && hasAvailableKiosk) {
            reloadContent();
        } else if (!hasAvailableKiosk) {
            showKioskUnavailableError();
        }
    }

    @Override
    protected void doInitialLoadLogic() {
        if (hasAvailableKiosk) {
            super.doInitialLoadLogic();
        } else {
            showKioskUnavailableError();
        }
    }

    private void updateSelectedDefaultKiosk() {
        try {
            selectedServiceId = ServiceHelper.getSelectedServiceId(requireContext());
            serviceId = selectedServiceId;

            KioskList kioskList = NewPipe.getService(serviceId).getKioskList();
            kioskId = kioskList.getDefaultKioskId();
            if (kioskId == null && !kioskList.getAvailableKiosks().isEmpty()) {
                kioskId = kioskList.getAvailableKiosks().iterator().next();
            }

            if (kioskId == null) {
                clearResolvedKiosk();
                return;
            }

            final ListLinkHandlerFactory handlerFactory =
                    kioskList.getListLinkHandlerFactoryByType(kioskId);
            if (handlerFactory == null) {
                clearResolvedKiosk();
                return;
            }
            url = handlerFactory.fromId(kioskId).getUrl();
            if (url == null || url.isEmpty()) {
                clearResolvedKiosk();
                return;
            }

            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
            name = kioskTranslatedName;

            hasAvailableKiosk = true;
            currentInfo = null;
            currentNextPage = null;
        } catch (final ExtractionException e) {
            clearResolvedKiosk();
            showError(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"));
        }
    }

    private void clearResolvedKiosk() {
        hasAvailableKiosk = false;
        kioskId = "";
        url = "";
        currentInfo = null;
        currentNextPage = null;
    }

    private void showKioskUnavailableError() {
        showError(new ErrorInfo(new ExtractionException("The selected service has no home feed"),
                UserAction.REQUESTED_KIOSK, "Loading default kiosk for selected service"));
    }
}
