package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import com.b44t.messenger.DcChat;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEventCenter;

import org.thoughtcrime.securesms.connect.ApplicationDcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.ViewUtil;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends PassphraseRequiredActionBarActivity
                             implements DcEventCenter.DcEventDelegate
{

  @SuppressWarnings("unused")
  private final static String TAG = ProfileActivity.class.getSimpleName();

  public static final String CHAT_ID_EXTRA    = "chat_id";
  public static final String CONTACT_ID_EXTRA = "contact_id";
  public static final String FORCE_TAB_EXTRA  = "force_tab";

  public static final int TAB_SETTINGS = 10;
  public static final int TAB_GALLERY  = 20;
  public static final int TAB_DOCS     = 30;
  public static final int TAB_LINKS    = 40;
  public static final int TAB_MAP      = 50;

  private static final int REQUEST_CODE_PICK_RINGTONE = 1;

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ApplicationDcContext dcContext;
  private int                  chatId;
  private boolean              chatIsGroup;
  private int                  contactId;

  private ArrayList<Integer> tabs = new ArrayList<>();
  private Toolbar            toolbar;
  private TabLayout          tabLayout;
  private ViewPager          viewPager;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    dcContext = DcHelper.getContext(this);
  }

  @Override
  protected void onCreate(Bundle bundle, boolean ready) {
    setContentView(R.layout.profile_activity);

    initializeResources();
    initializeToolbar();

    this.tabLayout.setupWithViewPager(viewPager);
    this.viewPager.setAdapter(new ProfilePagerAdapter(getSupportFragmentManager()));
    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_CHAT_MODIFIED, this);
    dcContext.eventCenter.addObserver(DcContext.DC_EVENT_CONTACTS_CHANGED, this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();

    if (chatId!=0) {
      inflater.inflate(R.menu.profile_chat, menu);
      if(!chatIsGroup) {
        menu.findItem(R.id.edit_group_name_and_image).setVisible(false);
      }
    }

    if (isContactProfile()) {
      inflater.inflate(R.menu.profile_contact, menu);
    }

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onDestroy() {
    dcContext.eventCenter.removeObservers(this);
    super.onDestroy();
  }

  @Override
  public void handleEvent(int eventId, Object data1, Object data2) {
    updateToolbar();
  }

  private void initializeResources() {
    chatId      = getIntent().getIntExtra(CHAT_ID_EXTRA, 0);
    contactId   = getIntent().getIntExtra(CONTACT_ID_EXTRA, 0);
    chatIsGroup = false;

    if (contactId!=0) {
      chatId = dcContext.getChatIdByContactId(contactId);
    }
    else if(chatId!=0) {
      DcChat dcChat = dcContext.getChat(chatId);
      chatIsGroup = dcChat.isGroup();
      if(!chatIsGroup) {
        final int[] members = dcContext.getChatContacts(chatId);
        contactId = members.length>=1? members[0] : 0;
      }
    }

    if(!isGlobalProfile() && !isSelfProfile()) {
      tabs.add(TAB_SETTINGS);
    }
    tabs.add(TAB_GALLERY);
    tabs.add(TAB_DOCS);
    //tabs.add(TAB_LINKS);
    //if(Prefs.isLocationStreamingEnabled(this)) {
    //  tabs.add(TAB_MAP);
    //}

    this.viewPager = ViewUtil.findById(this, R.id.pager);
    this.toolbar   = ViewUtil.findById(this, R.id.toolbar);
    this.tabLayout = ViewUtil.findById(this, R.id.tab_layout);
  }

  private void initializeToolbar()
  {
    setSupportActionBar(this.toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    updateToolbar();
  }

  private void updateToolbar() {
    if (isGlobalProfile()){
      getSupportActionBar().setTitle(R.string.menu_all_media);
    }
    else if (isContactProfile()){
      getSupportActionBar().setTitle(dcContext.getContact(contactId).getName());
    }
    else if (chatId >= 0) {
      getSupportActionBar().setTitle(dcContext.getChat(chatId).getName());
    }
  }

  private boolean isGlobalProfile() {
    return contactId==0 && chatId==0;
  }

  private boolean isContactProfile() {
    // there may still be a single-chat lined to the contact profile
    return contactId!=0 && (chatId==0 || !chatIsGroup);
  }

  private boolean isSelfProfile() {
    return isContactProfile() && contactId==DcContact.DC_CONTACT_ID_SELF;
  }

  private class ProfilePagerAdapter extends FragmentStatePagerAdapter {

    ProfilePagerAdapter(FragmentManager fragmentManager) {
      super(fragmentManager);
    }

    @Override
    public Fragment getItem(int position) {
      int tabId = tabs.get(position);
      Fragment fragment;
      Bundle args = new Bundle();

      switch(tabId) {
        case TAB_SETTINGS:
          fragment = new ProfileSettingsFragment();
          args.putInt(ProfileSettingsFragment.CHAT_ID_EXTRA, (chatId==0&&!isGlobalProfile())? -1 : chatId);
          args.putInt(ProfileSettingsFragment.CONTACT_ID_EXTRA, (contactId==0&&!isGlobalProfile())? -1 : contactId);
          args.putSerializable(ProfileSettingsFragment.LOCALE_EXTRA, dynamicLanguage.getCurrentLocale());
          break;

        case TAB_GALLERY:
          fragment = new ProfileGalleryFragment();
          args.putInt(ProfileGalleryFragment.CHAT_ID_EXTRA, (chatId==0&&!isGlobalProfile())? -1 : chatId);
          args.putSerializable(ProfileGalleryFragment.LOCALE_EXTRA, dynamicLanguage.getCurrentLocale());
          break;

        default:
          fragment = new ProfileDocumentsFragment();
          args.putInt(ProfileGalleryFragment.CHAT_ID_EXTRA, (chatId==0&&!isGlobalProfile())? -1 : chatId);
          args.putSerializable(ProfileDocumentsFragment.LOCALE_EXTRA, dynamicLanguage.getCurrentLocale());
          break;
      }

      fragment.setArguments(args);
      return fragment;
    }

    @Override
    public int getCount() {
      return tabs.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
      int tabId = tabs.get(position);
      switch(tabId) {
        case TAB_SETTINGS:
          if(isContactProfile()) {
            return getString(contactId==DcContact.DC_CONTACT_ID_SELF? R.string.self : R.string.tab_contact);
          }
          else {
            return getString(R.string.tab_members);
          }

        case TAB_GALLERY:
          return getString(R.string.tab_gallery);

        case TAB_DOCS:
          return getString(R.string.tab_docs);

        case TAB_LINKS:
          return getString(R.string.tab_links);

        case TAB_MAP:
          return getString(R.string.tab_map);

        default:
          throw new AssertionError();
      }
    }
  }


  // handle events
  // =========================================================================

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.menu_mute_notifications:
        onNotifyOnOff();
        break;
      case R.id.menu_sound:
        onSoundSettings();
        break;
      case R.id.menu_vibrate:
        onVibrateSettings();
        break;
      case R.id.edit_group_name_and_image:
        onEditGroupNameAndImage();
        break;
      case R.id.edit_contact_name:
        onEditContactName();
        break;
      case R.id.show_encr_info:
        onEncrInfo();
        break;
      case R.id.block_contact:
        onBlockContact();
        break;
    }

    return false;
  }

  public void onNotifyOnOff() {
    if (Prefs.isChatMuted(this, chatId)) {
      setMuted(0);
    }
    else {
      MuteDialog.show(this, until -> setMuted(until));
    }
  }

  private void setMuted(final long until) {
    if(chatId!=0) {
      Prefs.setChatMutedUntil(this, chatId, until);
    }
  }

  public void onSoundSettings() {
    Uri current = dcContext.getRecipient(dcContext.getChat(chatId)).getMessageRingtone();
    Uri defaultUri = Prefs.getNotificationRingtone(this);

    if      (current == null)              current = Settings.System.DEFAULT_NOTIFICATION_URI;
    else if (current.toString().isEmpty()) current = null;

    Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current);

    startActivityForResult(intent, REQUEST_CODE_PICK_RINGTONE);
  }

  public void onVibrateSettings() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.pref_vibrate)
        .setItems(R.array.recipient_vibrate_entries, (dialog, which) -> {
          Prefs.setChatVibrate(this, chatId, Prefs.VibrateState.fromId(which));
        })
        .show();
  }

  public void onEditGroupNameAndImage() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    intent.putExtra(GroupCreateActivity.EDIT_GROUP_CHAT_ID, chatId);
    if (dcContext.getChat(chatId).isVerified()) {
      intent.putExtra(GroupCreateActivity.GROUP_CREATE_VERIFIED_EXTRA, true);
    }
    startActivity(intent);
  }

  public void onEditContactName() {
    DcContact dcContact = dcContext.getContact(contactId);
    final EditText txt = new EditText(this);
    txt.setText(dcContact.getName());
    new AlertDialog.Builder(this)
        .setTitle(R.string.menu_edit_name)
        .setView(txt)
        .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
          String newName = txt.getText().toString();
          dcContext.createContact(newName, dcContact.getAddr());
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  public void onEncrInfo() {
    String info_str = dcContext.getContactEncrInfo(contactId);
    new AlertDialog.Builder(this)
        .setMessage(info_str)
        .setPositiveButton(android.R.string.ok, null)
        .show();
  }

  public void onBlockContact() {
    DcContact dcContact = dcContext.getContact(contactId);
    if(dcContact.isBlocked()) {
      new AlertDialog.Builder(this)
          .setMessage(R.string.ask_unblock_contact)
          .setCancelable(true)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.menu_unblock_contact, (dialog, which) -> {
            dcContext.blockContact(contactId, 0);
          }).show();
    }
    else {
      new AlertDialog.Builder(this)
          .setMessage(R.string.ask_block_contact)
          .setCancelable(true)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.menu_block_contact, (dialog, which) -> {
            dcContext.blockContact(contactId, 1);
          }).show();
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode==REQUEST_CODE_PICK_RINGTONE && resultCode== Activity.RESULT_OK && data!=null) {
      Uri value = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
      Uri defaultValue = Prefs.getNotificationRingtone(this);

      if (defaultValue.equals(value)) value = null;
      else if (value == null)         value = Uri.EMPTY;

      Prefs.setChatRingtone(this, chatId, value);
    }
  }

}
