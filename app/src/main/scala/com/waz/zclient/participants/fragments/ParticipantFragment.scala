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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.{Fragment, FragmentManager}
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.User.ConnectionStatus._
import com.waz.api._
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.threading.Threading
import com.waz.utils.events.Subscription
import com.waz.utils.returning
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.common.controllers.{BrowserController, UserAccountsController}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.connect.IConnectStore
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.{BlockedUserProfileFragment, ConnectRequestLoadMode, PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.pages.main.participants._
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.{OptionsMenuFragment, ParticipantsController}
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.usersearch.PickUserFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.{DefaultPageTransitionAnimation, LoadingIndicatorView}
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}

class ParticipantFragment extends BaseFragment[ParticipantFragment.Container] with FragmentHelper
  with ConversationScreenControllerObserver
  with OnBackPressedListener
  with ParticipantHeaderFragment.Container
  with ParticipantsBodyFragment.Container
  with SendConnectRequestFragment.Container
  with BlockedUserProfileFragment.Container
  with PendingConnectRequestFragment.Container
  with PickUserFragment.Container
  with SingleOtrClientFragment.Container {

  implicit def ctx: Context = getActivity
  import Threading.Implicits.Ui

  private lazy val bodyContainer = view[View](R.id.fl__participant__container)
  private lazy val participantsContainerView = view[View](R.id.ll__participant__container)
  private lazy val pickUserContainerView = view[View](R.id.fl__add_to_conversation__pickuser__container)

  private lazy val convChange = convController.convChanged.filter { _.to.isDefined }

  private lazy val loadingIndicatorView = returning( view[LoadingIndicatorView](R.id.liv__participants__loading_indicator) ) {
    _.foreach(_.setColor(getColorWithTheme(R.color.people_picker__loading__color, ctx)))
  }

  private lazy val browserController      = inject[BrowserController]
  private lazy val convController         = inject[ConversationController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val pickUserController     = inject[IPickUserController]
  private lazy val singleImageController  = inject[ISingleImageController]
  private lazy val navigationController   = inject[INavigationController]

  private var subs = Set.empty[Subscription]

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    if (nextAnim == 0 || Option(getContainer).isEmpty || getControllerFactory.isTornDown)
      super.onCreateAnimation(transit, enter, nextAnim)
    else new DefaultPageTransitionAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_medium),
      if (enter) getInt(R.integer.framework_animation_duration_medium) else 0,
      1f
    )

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_participant, container, false)) { _ =>
      val fragmentManager = getChildFragmentManager
      Option(fragmentManager.findFragmentById(R.id.fl__participant__overlay)).foreach {
        fragmentManager.beginTransaction.remove(_).commit
      }
    }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    val fragmentManager = getChildFragmentManager

    if (Option(savedInstanceState).isEmpty) {
      fragmentManager.beginTransaction
        .replace(
          R.id.fl__participant__header__container,
          ParticipantHeaderFragment.newInstance(),
          ParticipantHeaderFragment.TAG
        )
        .commit

      participantsController.isGroupOrBot.head.foreach {
        case false =>
          fragmentManager.beginTransaction
            .replace(
              R.id.fl__participant__container,
              TabbedParticipantBodyFragment.newInstance(getArguments.getInt(ParticipantFragment.ARG__FIRST__PAGE)),
              TabbedParticipantBodyFragment.TAG)
            .commit
        case _ =>
          fragmentManager.beginTransaction
            .replace(R.id.fl__participant__container,
              ParticipantsBodyFragment.newInstance(),
              ParticipantsBodyFragment.TAG)
            .commit
      }

      fragmentManager.beginTransaction
        .replace(
          R.id.fl__participant__settings_box,
          OptionsMenuFragment.newInstance(false),
          OptionsMenuFragment.Tag
        )
        .commit
    }

    bodyContainer
    loadingIndicatorView
    participantsContainerView
    pickUserContainerView

    subs += convChange.map(_.requester).onUi {
      case START_CONVERSATION | START_CONVERSATION_FOR_VIDEO_CALL | START_CONVERSATION_FOR_CALL | START_CONVERSATION_FOR_CAMERA =>
        getChildFragmentManager.popBackStackImmediate(PickUserFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        pickUserController.hidePickUserWithoutAnimations(getCurrentPickerDestination)
      case _ =>
    }

    subs += participantsController.conv.map(_.isActive).onUi { screenController.setMemberOfConversation }

    subs += convController.currentConvId.onUi { convId =>
      val iConv = convController.iConv(convId)
      getStoreFactory.participantsStore.setCurrentConversation(iConv)
    }

    subs += participantsController.isGroupOrBot.onUi { isGroupOrBot =>
      screenController.setSingleConversation(!isGroupOrBot)
    }

  }

  override def onStart(): Unit = {
    super.onStart()
    screenController.addConversationControllerObservers(this)
  }

  override def onStop(): Unit = {
    getStoreFactory.participantsStore.setCurrentConversation(null)
    screenController.removeConversationControllerObservers(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    singleImageController.clearReferences()
    subs.foreach(_.destroy())
    subs = Set.empty[Subscription]

    super.onDestroyView()
  }

  override def onBackPressed: Boolean = withBackstackHead {
    case Some(f: PickUserFragment) if f.onBackPressed() => true
    case Some(f: SingleOtrClientFragment) =>
      screenController.hideOtrClient()
      true
    case Some(f: OptionsMenuFragment) if f.close() => true
    case _ if pickUserController.isShowingPickUser(getCurrentPickerDestination) =>
      pickUserController.hidePickUser(getCurrentPickerDestination)
      true
    case _ if screenController.isShowingUser =>
      screenController.hideUser()
      true
    case _ if screenController.isShowingParticipant =>
      screenController.hideParticipants(true, false)
      true
    case _ => false
  }

  override def onShowEditConversationName(show: Boolean): Unit =
    bodyContainer.foreach { view =>
      if (show) ViewUtils.fadeOutView(view)
      else ViewUtils.fadeInView(view)
    }

  override def onAddPeopleToConversation(): Unit =
    pickUserController.showPickUser(IPickUserController.Destination.PARTICIPANTS)

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (!inConvList) getChildFragmentManager.findFragmentByTag(OptionsMenuFragment.Tag) match {
      case fragment: OptionsMenuFragment => fragment.open(convId)
      case _ =>
    }

  def showOtrClient(userId: UserId, clientId: ClientId): Unit =
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.open_profile,
        R.anim.close_profile,
        R.anim.open_profile,
        R.anim.close_profile
      )
      .add(
        R.id.fl__participant__overlay,
        SingleOtrClientFragment.newInstance(userId, clientId),
        SingleOtrClientFragment.TAG
      )
      .addToBackStack(SingleOtrClientFragment.TAG)
      .commit

  override def onShowUser(userId: UserId): Unit = participantsController.getUser(userId).foreach {
    case None =>

    case Some(user) if user.isSelf =>
      openUserProfileFragment(SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)
      navigationController.setRightPage(Page.PARTICIPANT_USER_PROFILE, ParticipantFragment.TAG)

    case Some(_) if userAccountsController.isTeamAccount && userAccountsController.isTeamMember(userId) =>
      showAcceptedUser(userId)

    case Some(user) if user.connection == User.ConnectionStatus.ACCEPTED =>
      showAcceptedUser(user.id)

    case Some(user) if user.connection == PENDING_FROM_OTHER || user.connection == PENDING_FROM_USER || user.connection == IGNORED =>
      openUserProfileFragment(
        PendingConnectRequestFragment.newInstance(userId.str, null, ConnectRequestLoadMode.LOAD_BY_USER_ID, IConnectStore.UserRequester.PARTICIPANTS),
        PendingConnectRequestFragment.TAG
      )
      navigationController.setRightPage(Page.PARTICIPANT_USER_PROFILE, ParticipantFragment.TAG)

    case Some(user) if user.connection == BLOCKED =>
      openUserProfileFragment(
        BlockedUserProfileFragment.newInstance(userId.str, IConnectStore.UserRequester.PARTICIPANTS),
        BlockedUserProfileFragment.TAG
      )
      navigationController.setRightPage(Page.PARTICIPANT_USER_PROFILE, ParticipantFragment.TAG)

    case Some(user) if user.connection == CANCELLED || user.connection == UNCONNECTED =>
      openUserProfileFragment(
        SendConnectRequestFragment.newInstance(userId.str, IConnectStore.UserRequester.PARTICIPANTS),
        SendConnectRequestFragment.TAG
      )
      navigationController.setRightPage(Page.SEND_CONNECT_REQUEST, ParticipantFragment.TAG)
  }

  private def showAcceptedUser(userId: UserId) = {
    participantsController.selectParticipant(userId)
    openUserProfileFragment(SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)
    navigationController.setRightPage(Page.PARTICIPANT_USER_PROFILE, ParticipantFragment.TAG)
  }

  private def openUserProfileFragment(fragment: Fragment, tag: String) = {
    val transaction = getChildFragmentManager.beginTransaction
    if (
      screenController.getPopoverLaunchMode != DialogLaunchMode.AVATAR &&
      screenController.getPopoverLaunchMode != DialogLaunchMode.COMMON_USER
    ) {
      animateParticipantsWithConnectUserProfile(false)
      transaction.setCustomAnimations(R.anim.open_profile, R.anim.close_profile, R.anim.open_profile, R.anim.close_profile)
    }
    else transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
    transaction.add(R.id.fl__participant__overlay, fragment, tag).addToBackStack(tag).commit
  }

  private def animateParticipantsWithConnectUserProfile(show: Boolean) = {
    val animator = participantsContainerView.animate
    if (show) {
      animator.alpha(1)
        .scaleY(1)
        .scaleX(1)
        .setInterpolator(new Expo.EaseOut)
        .setDuration(getInt(R.integer.reopen_profile_source__animation_duration))
        .setStartDelay(getInt(R.integer.reopen_profile_source__delay))
    } else {
      animator.alpha(0)
        .scaleY(2)
        .scaleX(2)
        .setInterpolator(new Expo.EaseIn)
        .setDuration(getInt(R.integer.reopen_profile_source__animation_duration))
        .setStartDelay(0)
    }
    animator.start()
  }

  override def onHideUser(): Unit = if (screenController.isShowingUser) {
    getChildFragmentManager.popBackStackImmediate
    getControllerFactory.getNavigationController.setRightPage(if (screenController.isShowingParticipant) Page.PARTICIPANT else Page.MESSAGE_STREAM, ParticipantFragment.TAG)
    animateParticipantsWithConnectUserProfile(true)
  }

  override def showRemoveConfirmation(userId: UserId): Unit =
    participantsController.showRemoveConfirmation(userId)

  override def dismissUserProfile(): Unit = screenController.hideUser()

  override def dismissSingleUserProfile(): Unit = dismissUserProfile()

  override def onAcceptedConnectRequest(conversation: ConvId): Unit = {
    screenController.hideUser()
    verbose(s"onAcceptedConnectRequest $conversation")
    convController.selectConv(conversation, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onUnblockedUser(restoredConversationWithUser: ConvId): Unit = {
    screenController.hideUser()
    verbose(s"onUnblockedUser $restoredConversationWithUser")
    convController.selectConv(restoredConversationWithUser, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onConnectRequestWasSentToUser(): Unit = screenController.hideUser()

  override def getLoadingViewIndicator: LoadingIndicatorView = loadingIndicatorView

  override def getCurrentPickerDestination = IPickUserController.Destination.PARTICIPANTS

  override def onOpenUrl(url: String) =
    browserController.openUrl(AndroidURIUtil.parse(url))

  override def onShowParticipants(anchorView: View, isSingleConversation: Boolean, isMemberOfConversation: Boolean, showDeviceTabIfSingle: Boolean): Unit = {}

  override def onHideParticipants(backOrButtonPressed: Boolean, hideByConversationChange: Boolean, isSingleConversation: Boolean): Unit = {}

  override def onHeaderViewMeasured(participantHeaderHeight: Int): Unit = {}

  override def onHideOtrClient(): Unit = getChildFragmentManager.popBackStackImmediate

  override def onShowLikesList(message: Message): Unit = {}

  override def onShowIntegrationDetails(providerId: ProviderId, integrationId: IntegrationId): Unit = {}

  override def onConversationUpdated(conversation: ConvId): Unit = {}

  override def showIncomingPendingConnectRequest(conv: ConvId): Unit = {}
}

object ParticipantFragment {
  val TAG: String = classOf[ParticipantFragment].getName
  private val ARG_USER_REQUESTER = "ARG_USER_REQUESTER"
  private val ARG__FIRST__PAGE = "ARG__FIRST__PAGE"

  def newInstance(userRequester: IConnectStore.UserRequester, firstPage: Int): ParticipantFragment =
    returning(new ParticipantFragment) {
      _.setArguments(returning(new Bundle) { args =>
        args.putSerializable(ARG_USER_REQUESTER, userRequester)
        args.putInt(ARG__FIRST__PAGE, firstPage)
      })
    }

  trait Container {}

}
