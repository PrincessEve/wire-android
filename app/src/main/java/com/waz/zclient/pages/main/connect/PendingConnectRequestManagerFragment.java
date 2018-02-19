/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.pages.main.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.waz.api.NetworkMode;
import com.waz.model.ConvId;
import com.waz.model.UserId;
import com.waz.zclient.OnBackPressedListener;
import com.waz.zclient.R;
import com.waz.zclient.controllers.navigation.Page;
import com.waz.zclient.core.stores.connect.IConnectStore;
import com.waz.zclient.core.stores.network.NetworkAction;
import com.waz.zclient.pages.BaseFragment;
import com.waz.zclient.participants.OptionsMenuFragment;
import com.waz.zclient.utils.ViewUtils;

public class PendingConnectRequestManagerFragment extends BaseFragment<PendingConnectRequestManagerFragment.Container> implements PendingConnectRequestFragment.Container,
                                                                                                                                  OnBackPressedListener {

    public static final String TAG = PendingConnectRequestManagerFragment.class.getName();
    public static final String ARGUMENT_USER_ID = "ARGUMENT_USER_ID";
    public static final String ARGUMENT_CONVERSATION_ID = "ARGUMENT_CONVERSATION_ID";
    public static final String ARGUMENT_LOAD_MODE = "ARGUMENT_LOAD_MODE";
    public static final String ARGUMENT_USER_REQUESTER = "ARGUMENT_USER_REQUESTER";

    private IConnectStore.UserRequester userRequester;

    public static PendingConnectRequestManagerFragment newInstance(String userId, String conversationId, ConnectRequestLoadMode loadMode, IConnectStore.UserRequester userRequester) {
        PendingConnectRequestManagerFragment newFragment = new PendingConnectRequestManagerFragment();

        Bundle args = new Bundle();
        args.putString(ARGUMENT_USER_ID, userId);
        args.putString(ARGUMENT_CONVERSATION_ID, conversationId);
        args.putString(ARGUMENT_USER_REQUESTER, userRequester.toString());
        args.putString(ARGUMENT_LOAD_MODE, loadMode.toString());
        newFragment.setArguments(args);

        return newFragment;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Lifecycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_request_pending_manager, container, false);

        if (savedInstanceState == null) {
            String userId = getArguments().getString(ARGUMENT_USER_ID);
            String conversationId = getArguments().getString(ARGUMENT_CONVERSATION_ID);
            ConnectRequestLoadMode loademode = ConnectRequestLoadMode.valueOf(getArguments().getString(ARGUMENT_LOAD_MODE));
            userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));

            getChildFragmentManager()
                    .beginTransaction()
                    .add(R.id.fl__pending_connect_request, PendingConnectRequestFragment.newInstance(userId, conversationId, loademode, userRequester), PendingConnectRequestFragment.TAG)
                    .commit();

            getChildFragmentManager().beginTransaction()
                                     .add(R.id.fl__pending_connect_request__settings_box,
                                          OptionsMenuFragment.newInstance(false),
                                          OptionsMenuFragment.Tag())
                                     .commit();
        }

        return view;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // UserProfileContainer
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void dismissUserProfile() {
        getContainer().dismissUserProfile();
    }

    @Override
    public void dismissSingleUserProfile() {
        if (getChildFragmentManager().popBackStackImmediate()) {
            restoreCurrentPageAfterClosingOverlay();
        }
    }

    @Override
    public void showRemoveConfirmation(final UserId userId) {
        getStoreFactory().networkStore().doIfHasInternetOrNotifyUser(new NetworkAction() {
            @Override
            public void execute(NetworkMode networkMode) {
                getContainer().showRemoveConfirmation(userId);
            }

            @Override
            public void onNoNetwork() {
                ViewUtils.showAlertDialog(getActivity(),
                                          R.string.alert_dialog__no_network__header,
                                          R.string.remove_from_conversation__no_network__message,
                                          R.string.alert_dialog__confirmation,
                                          null, true);
            }
        });
    }

    @Override
    public void onConversationUpdated(ConvId conversation) {
        getContainer().onAcceptedPendingOutgoingConnectRequest(conversation);
    }

    private void restoreCurrentPageAfterClosingOverlay() {
        if (getControllerFactory() == null || getControllerFactory().isTornDown()) {
            return;
        }

        IConnectStore.UserRequester userRequester = IConnectStore.UserRequester.valueOf(getArguments().getString(ARGUMENT_USER_REQUESTER));
        if (userRequester == IConnectStore.UserRequester.CONVERSATION) {
            getControllerFactory().getNavigationController().setRightPage(Page.PENDING_CONNECT_REQUEST_AS_CONVERSATION, TAG);
        } else {
            getControllerFactory().getNavigationController().setRightPage(Page.PENDING_CONNECT_REQUEST, TAG);
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    // PendingConnectRequestFragment
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAcceptedConnectRequest(ConvId conversation) {
        getContainer().onAcceptedConnectRequest(conversation);
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    public interface Container extends UserProfileContainer {
        void onAcceptedConnectRequest(ConvId conversation);

        void onAcceptedPendingOutgoingConnectRequest(ConvId conversation);

    }
}
