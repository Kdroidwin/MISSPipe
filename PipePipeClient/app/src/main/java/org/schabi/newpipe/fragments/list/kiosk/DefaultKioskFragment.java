package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

public class DefaultKioskFragment extends KioskFragment {
    private int selectedServiceId = -1;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (serviceId < 0) {
            updateSelectedDefaultKiosk();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (selectedServiceId != ServiceHelper.getSelectedServiceId(requireContext())) {
            if (currentWorker != null) {
                currentWorker.dispose();
            }
            updateSelectedDefaultKiosk();
            reloadContent();
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
                // Some source-only services intentionally have no home feed. A restored front-page
                // tab must still be renderable, so use the app's established default home source.
                serviceId = ServiceList.MissAV.getServiceId();
                kioskList = NewPipe.getService(serviceId).getKioskList();
                kioskId = kioskList.getDefaultKioskId();
            }

            if (kioskId == null) {
                throw new ExtractionException("No default kiosk is available");
            }

            final ListLinkHandlerFactory handlerFactory =
                    kioskList.getListLinkHandlerFactoryByType(kioskId);
            url = handlerFactory.fromId(kioskId).getUrl();

            kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, requireContext());
            name = kioskTranslatedName;

            currentInfo = null;
            currentNextPage = null;
        } catch (final ExtractionException e) {
            showError(new ErrorInfo(e, UserAction.REQUESTED_KIOSK,
                    "Loading default kiosk for selected service"));
        }
    }
}
