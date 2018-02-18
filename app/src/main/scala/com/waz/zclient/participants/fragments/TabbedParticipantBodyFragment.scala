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
import android.support.v4.app.Fragment
import android.support.v4.view.ViewPager
import android.view.animation.{AlphaAnimation, Animation}
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.ZLog.ImplicitTag._
import com.waz.api.impl.otr
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.zclient.common.controllers.{BrowserController, ThemeController, UserAccountsController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.{ParticipantOtrDeviceAdapter, ParticipantsController, TabbedParticipantPagerAdapter}
import com.waz.zclient.ui.views.tab.TabIndicatorLayout
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.menus.FooterMenuCallback
import com.waz.zclient.{BaseActivity, FragmentHelper, R}

class TabbedParticipantBodyFragment extends FragmentHelper {
  import Threading.Implicits.Ui
  implicit def ctx: Context = getActivity

  private lazy val participantOtrDeviceAdapter = new ParticipantOtrDeviceAdapter

  private var viewPager = Option.empty[ViewPager]

  private lazy val convController         = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController        = inject[ThemeController]
  private lazy val screenController       = inject[IConversationScreenController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val browserController      = inject[BrowserController]

  private lazy val callback = new FooterMenuCallback {

    override def onLeftActionClicked(): Unit =
      participantsController.isGroup.head.foreach {
        case false if userAccountsController.hasCreateConversationPermission =>
          screenController.addPeopleToConversation()
        case _ =>
          screenController.hideParticipants(true, false)
          participantsController.otherParticipant.head.foreach {
            case Some(userId) =>
              userAccountsController.createAndOpenConversation(
                Array[UserId](userId),
                ConversationChangeRequester.START_CONVERSATION,
                getActivity.asInstanceOf[BaseActivity]
              )
            case _ =>
          }
      }
    override def onRightActionClicked(): Unit = (for {
      isGroup <- participantsController.isGroup.head
      convId  <- convController.currentConvId.head
    } yield (isGroup, convId)).foreach {
      case (false, convId) => screenController.showConversationMenu(false, convId)
      case (true, convId) if userAccountsController.hasRemoveConversationMemberPermission(convId) =>
        participantsController.otherParticipant.head.foreach {
          case Some(userId) => participantsController.showRemoveConfirmation(userId)
          case _            =>
        }
      case _ =>
    }
  }

  // This is a workaround for the bug where child fragments disappear when
  // the parent is removed (as all children are first removed from the parent)
  // See https://code.google.com/p/android/issues/detail?id=55228
  // Apply the workaround only if this is a child fragment, and the parent is being removed.
  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    Option(getParentFragment) match {
      case Some(parent: Fragment) if enter && parent.isRemoving => returning(new AlphaAnimation(1, 1)){
        _.setDuration(ViewUtils.getNextAnimationDuration(parent))
      }
      case _ => super.onCreateAnimation(transit, enter, nextAnim)
    }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_participants_single_tabbed, viewGroup, false)) { v =>
      viewPager = Option(returning(findById[ViewPager](v, R.id.vp_single_participant_viewpager)) { pager =>
        pager.setAdapter(new TabbedParticipantPagerAdapter(participantOtrDeviceAdapter, callback))

        val tabIndicatorLayout = findById[TabIndicatorLayout](v, R.id.til_single_participant_tabs)
        tabIndicatorLayout.setPrimaryColor(getColorWithTheme(
          if (themeController.isDarkTheme) R.color.text__secondary_dark else R.color.text__secondary_light,
          getContext
        ))

        tabIndicatorLayout.setViewPager(pager)
      })

    }

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    if (Option(savedInstanceState).isEmpty) viewPager.foreach { pager =>
      if (screenController.shouldShowDevicesTab) {
        pager.setCurrentItem(1)
        screenController.setShowDevicesTab(null)
      } else
        pager.setCurrentItem(getArguments.getInt(TabbedParticipantBodyFragment.ARG__FIRST__PAGE))
    }

    participantOtrDeviceAdapter.onClientClick.onUi { client =>
      participantsController.otherParticipant.head.foreach {
        case Some(userId) =>
          val otrClient = new otr.OtrClient(userId, client.id, client)(ZMessaging.currentUi)
          screenController.showOtrClient(otrClient, getStoreFactory.pickUserStore.getUser(userId.str))
        case _ =>
      }
    }

    participantOtrDeviceAdapter.onHeaderClick {
      _ => browserController.openUrl(getString(R.string.url_otr_learn_why))
    }

    participantsController.showParticipantsRequest.onUi {
      case (view, showDeviceTabIfSingle) => viewPager.foreach { pager =>
        if (screenController.shouldShowDevicesTab) {
          pager.setCurrentItem(1)
          screenController.setShowDevicesTab(null)
        } else participantsController.isGroup.head.foreach { isGroup =>
          if (!isGroup && showDeviceTabIfSingle) pager.setCurrentItem(1)
        }
      }
    }

  }

  override def onDestroyView(): Unit = {
    viewPager = None
    super.onDestroyView()
  }

}

object TabbedParticipantBodyFragment {
  val TAG: String = classOf[TabbedParticipantBodyFragment].getName
  private val ARG__FIRST__PAGE: String = "ARG__FIRST__PAGE"
  val USER_PAGE: Int = 0
  val DEVICE_PAGE: Int = 1

  def newInstance(firstPage: Int): TabbedParticipantBodyFragment =
    returning(new TabbedParticipantBodyFragment){
      _.setArguments(returning(new Bundle){
        _.putInt(ARG__FIRST__PAGE, firstPage)
      })
    }
}
