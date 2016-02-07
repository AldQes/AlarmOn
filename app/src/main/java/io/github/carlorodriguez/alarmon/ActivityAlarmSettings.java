/****************************************************************************
 * Copyright 2010 kraigs.android@gmail.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ****************************************************************************/

package io.github.carlorodriguez.alarmon;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This activity is used for editing alarm settings.  Settings are broken
 * into two pieces: alarm information and actual settings.  Every alarm will
 * have alarm information.  Alarms will only have alarm settings if the user
 * has overridden the default settings for a given alarm.  This dialog is used
 * to edit both the application default settings and individual alarm settings.
 * When editing the application default settings, no AlarmInfo object will
 * be present.  When editing an alarm which hasn't yet had specific settings
 * set, AlarmSettings will contain the default settings.  There is one required
 * EXTRA that must be supplied when starting this activity: EXTRAS_ALARM_ID,
 * which should contain a long representing the alarmId of the settings
 * being edited.  AlarmSettings.DEFAULT_SETTINGS_ID can be used to edit the
 * default settings.
 */
public final class ActivityAlarmSettings extends Activity {
  public static final String EXTRAS_ALARM_ID = "alarm_id";
  private static final int MISSING_EXTRAS = -69;

  private enum SettingType {
    TIME,
    NAME,
    DAYS_OF_WEEK,
    TONE, SNOOZE,
    VIBRATE,
    VOLUME_FADE
  }

    public static final int TIME_PICKER = 0;
    public static final int NAME_PICKER = 1;
    public static final int DOW_PICKER = 2;
    public static final int TONE_PICKER = 3;
    public static final int SNOOZE_PICKER = 4;
    public static final int VOLUME_FADE_PICKER = 5;
    public static final int DELETE_CONFIRM = 6;

  private static long alarmId;
  private static AlarmClockServiceBinder service;
  private DbAccessor db;
  private AlarmInfo originalInfo;
  private static AlarmInfo info;
  private static AlarmSettings originalSettings;
  private static AlarmSettings settings;
  static SettingsAdapter settingsAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.settings);

      setTitle(getString(R.string.settings));

    // An alarm id is required in the extras bundle.
    alarmId = getIntent().getExtras().getLong(EXTRAS_ALARM_ID, MISSING_EXTRAS);
    if (alarmId == MISSING_EXTRAS) {
      throw new IllegalStateException("EXTRAS_ALARM_ID not supplied in intent.");
    }

    // Access to in-memory and persistent data structures.
    service = new AlarmClockServiceBinder(getApplicationContext());
    db = new DbAccessor(getApplicationContext());

    // Read the current settings from the database.  Keep a copy of the
    // original values so that we can write new values only if they differ
    // from the originals.
    originalInfo = db.readAlarmInfo(alarmId);
    // Info will not be available for the default settings.
    if (originalInfo != null) {
      info = new AlarmInfo(originalInfo);
    }
    originalSettings = db.readAlarmSettings(alarmId);
    settings = new AlarmSettings(originalSettings);

    // Setup individual UI elements.
    // Positive acknowledgment button.
    final Button okButton = (Button) findViewById(R.id.settings_ok);
    okButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        // Write AlarmInfo if it changed.
        if (originalInfo != null && !originalInfo.equals(info)) {
          db.writeAlarmInfo(alarmId, info);
          // Explicitly enable the alarm if the user changed the time.
          // This will reschedule the alarm if it was already enabled.
          // It's also probably the right thing to do if the alarm wasn't
          // enabled.
          if (!originalInfo.getTime().equals(info.getTime())) {
            service.scheduleAlarm(alarmId);
          }
        }
        // Write AlarmSettings if they have changed.
        if (!originalSettings.equals(settings)) {
          db.writeAlarmSettings(alarmId, settings);
        }
        finish();
      }
    });

    // Negative acknowledgment button.
    final Button cancelButton = (Button) findViewById(R.id.settings_cancel);
    cancelButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    // Delete button.  Simply opens a confirmation dialog (which does the
    // actual delete).
    Button deleteButton = (Button) findViewById(R.id.settings_delete);
    deleteButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        showDialogFragment(DELETE_CONFIRM);
      }
    });

    // Setup the list of settings.  Each setting is represented by a Setting
    // object.  Create one here for each setting type.
    final ArrayList<Setting> settingsObjects =
      new ArrayList<>(SettingType.values().length);
    // Only display AlarmInfo if the user is editing an actual alarm (as
    // opposed to the default application settings).
    if (alarmId != AlarmSettings.DEFAULT_SETTINGS_ID) {
      // The alarm time.
      settingsObjects.add(new Setting() {
        @Override
        public String name() { return getString(R.string.time); }
        @Override
        public String value() { return info.getTime().localizedString(getApplicationContext()); }
        @Override
        public SettingType type() { return SettingType.TIME; }
      });
      // A human-readable label for the alarm.
      settingsObjects.add(new Setting() {
        @Override
        public String name() { return getString(R.string.label); }
        @Override
        public String value() { return info.getName().equals("") ?
            getString(R.string.none) : info.getName(); }
        @Override
        public SettingType type() { return SettingType.NAME; }
      });
      // Days of the week this alarm should repeat.
      settingsObjects.add(new Setting() {
        @Override
        public String name() { return getString(R.string.repeat); }
        @Override
        public String value() { return info.getTime().getDaysOfWeek().toString(getApplicationContext()); }
        @Override
        public SettingType type() { return SettingType.DAYS_OF_WEEK; }
      });
    }
    // The notification tone used for this alarm.
    settingsObjects.add(new Setting() {
      @Override
      public String name() { return getString(R.string.tone); }
      @Override
      public String value() {
        String value = settings.getToneName();
        if (AppSettings.isDebugMode(getApplicationContext())) {
          value += " " + settings.getTone().toString();
        }
        return value;
      }
      @Override
      public SettingType type() { return SettingType.TONE; }
    });
    // The snooze duration for this alarm.
    settingsObjects.add(new Setting() {
      @Override
      public String name() { return getString(R.string.snooze_minutes); }
      @Override
      public String value() { return "" + settings.getSnoozeMinutes(); }
      @Override
      public SettingType type() { return SettingType.SNOOZE; }
    });
    // The vibrator setting for this alarm.
    settingsObjects.add(new Setting() {
      @Override
      public String name() { return getString(R.string.vibrate); }
      @Override
      public String value() { return settings.getVibrate() ?
          getString(R.string.enabled) : getString(R.string.disabled); }
      @Override
      public SettingType type() { return SettingType.VIBRATE; }
    });
    // How the volume should be controlled while this alarm is triggering.
    settingsObjects.add(new Setting() {
      @Override
      public String name() { return getString(R.string.alarm_fade); }
      @Override
      public String value() { return getString(R.string.fade_description,
          settings.getVolumeStartPercent(), settings.getVolumeEndPercent(),
          settings.getVolumeChangeTimeSec()); }
      @Override
      public SettingType type() { return SettingType.VOLUME_FADE; }
    });

    final ListView settingsList = (ListView) findViewById(R.id.settings_list);
    settingsAdapter = new SettingsAdapter(getApplicationContext(), settingsObjects);
    settingsList.setAdapter(settingsAdapter);
    settingsList.setOnItemClickListener(new SettingsListClickListener());

    // The delete button should not be shown when editing the default settings.
    if (alarmId == AlarmSettings.DEFAULT_SETTINGS_ID) {
      deleteButton.setVisibility(View.GONE);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    service.bind();
  }

  @Override
  protected void onPause() {
    super.onPause();
    service.unbind();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    db.closeConnections();
  }

  /**
   * This is a helper class for mapping SettingType to action.  Each Setting
   * in the list view returns a unique SettingType.  Trigger a dialog
   * based off of that SettingType.
   */
  private final class SettingsListClickListener implements OnItemClickListener {
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      final SettingsAdapter adapter = (SettingsAdapter) parent.getAdapter();
      SettingType type = adapter.getItem(position).type();
      switch (type) {
        case TIME:
          showDialogFragment(TIME_PICKER);
          break;

        case NAME:
          showDialogFragment(NAME_PICKER);
          break;

        case DAYS_OF_WEEK:
          showDialogFragment(DOW_PICKER);
          break;

        case TONE:
          showDialogFragment(TONE_PICKER);
          break;

        case SNOOZE:
          showDialogFragment(SNOOZE_PICKER);
          break;

        case VIBRATE:
          settings.setVibrate(!settings.getVibrate());
          settingsAdapter.notifyDataSetChanged();
          break;

        case VOLUME_FADE:
          showDialogFragment(VOLUME_FADE_PICKER);
          break;
      }
    }
  }

  private static int tryParseInt(String input, int fallback) {
    try {
      return Integer.parseInt(input);
    } catch (Exception e) {
      return fallback;
    }
  }

  /**
   * A helper interface to encapsulate the data displayed in the list view of
   * this activity.  Consists of a setting name, a setting value, and a type.
   * The type is used to trigger the appropriate action from the onClick
   * handler.
   */
  private abstract class Setting {
    public abstract String name();
    public abstract String value();
    public abstract SettingType type();
  }

  /**
   * This adapter populates the settings_items view with the data encapsulated
   * in the individual Setting objects.
   */
  private final class SettingsAdapter extends ArrayAdapter<Setting> {
    public SettingsAdapter(Context context, List<Setting> settingsObjects) {
      super(context, 0, settingsObjects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LayoutInflater inflater = getLayoutInflater();
      View row = inflater.inflate(R.layout.settings_item, null);
      TextView name = (TextView) row.findViewById(R.id.setting_name);
      TextView value = (TextView) row.findViewById(R.id.setting_value);
      Setting setting = getItem(position);
      name.setText(setting.name());
      value.setText(setting.value());
      return row;
    }
  }

    private void showDialogFragment(int id) {
        DialogFragment dialog = new ActivityDialogFragment().newInstance(
                id);
        dialog.show(getFragmentManager(), "ActivityDialogFragment");
    }

  public static class ActivityDialogFragment extends DialogFragment {

    public ActivityDialogFragment newInstance(int id) {
      ActivityDialogFragment fragment = new ActivityDialogFragment();
      Bundle args = new Bundle();
      args.putInt("id", id);
      fragment.setArguments(args);
      return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      switch (getArguments().getInt("id")) {
          case TIME_PICKER:
              final AlarmTime time = info.getTime();
              int hour = time.calendar().get(Calendar.HOUR_OF_DAY);
              int minute = time.calendar().get(Calendar.MINUTE);
              int second = time.calendar().get(Calendar.SECOND);
              return new TimePickerDialog(getActivity(), getString(R.string.time),
                      hour, minute, second, AppSettings.isDebugMode(getActivity()),
                      new TimePickerDialog.OnTimeSetListener() {
                          @Override
                          public void onTimeSet(int hourOfDay, int minute, int second) {
                              info.setTime(new AlarmTime(hourOfDay, minute, second, time.getDaysOfWeek()));
                              settingsAdapter.notifyDataSetChanged();
                              // Destroy this dialog so that it does not save its state.
                              dismiss();
                          }
                      });

          case NAME_PICKER:
              final View nameView = View.inflate(getActivity(),
                      R.layout.name_settings_dialog, null);
              final TextView label = (TextView) nameView.findViewById(R.id.name_label);
              label.setText(info.getName());
              final AlertDialog.Builder nameBuilder = new AlertDialog.Builder(getActivity());
              nameBuilder.setTitle(R.string.alarm_label);
              nameBuilder.setView(nameView);
              nameBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      info.setName(label.getEditableText().toString());
                      settingsAdapter.notifyDataSetChanged();
                      dismiss();
                  }
              });
              nameBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      dismiss();
                  }
              });
              return nameBuilder.create();

          case DOW_PICKER:
              final AlertDialog.Builder dowBuilder = new AlertDialog.Builder(getActivity());
              dowBuilder.setTitle(R.string.scheduled_days);
              dowBuilder.setMultiChoiceItems(
                      info.getTime().getDaysOfWeek().names(getActivity()),
                      info.getTime().getDaysOfWeek().bitmask(),
                      new OnMultiChoiceClickListener() {
                          @Override
                          public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                              if (isChecked) {
                                  info.getTime().getDaysOfWeek().addDay(Week.Day.values()[which]);
                              } else {
                                  info.getTime().getDaysOfWeek().removeDay(Week.Day.values()[which]);
                              }
                          }
                      });
              dowBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      settingsAdapter.notifyDataSetChanged();
                      dismiss();
                  }
              });
              return dowBuilder.create();

          case TONE_PICKER:
              MediaPickerDialog mediaPicker = new MediaPickerDialog(getActivity());
              mediaPicker.setPickListener(new MediaPickerDialog.OnMediaPickListener() {
                  @Override
                  public void onMediaPick(String name, Uri media) {
                      if (name.length() == 0) {
                          name = getString(R.string.unknown_name);
                      }
                      settings.setTone(media, name);
                      settingsAdapter.notifyDataSetChanged();
                  }
              });
              return mediaPicker;

          case SNOOZE_PICKER:
              // This currently imposes snooze times between 1 and 60 minutes,
              // which isn't really necessary, but I think the picker is easier
              // to use than a free-text field that you have to type numbers into.
              final CharSequence[] items = new CharSequence[60];
              // Note the array index is one-off from the value (the value of 1 is
              // at index 0).
              for (int i = 1; i <= 60; ++i) {
                  items[i-1] = Integer.toString(i);
              }
              final AlertDialog.Builder snoozeBuilder = new AlertDialog.Builder(getActivity());
              snoozeBuilder.setTitle(R.string.snooze_minutes);
              snoozeBuilder.setSingleChoiceItems(items, settings.getSnoozeMinutes() - 1,
                      new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int item) {
                              settings.setSnoozeMinutes(item + 1);
                              settingsAdapter.notifyDataSetChanged();
                              dismiss();
                          }
                      });
              return snoozeBuilder.create();

          case VOLUME_FADE_PICKER:
              final View fadeView = View.inflate(getActivity(), R.layout.fade_settings_dialog, null);
              final EditText volumeStart = (EditText) fadeView.findViewById(R.id.volume_start);
              String volumeStartText = "" + settings.getVolumeStartPercent();
              volumeStart.setText(volumeStartText );
              final EditText volumeEnd = (EditText) fadeView.findViewById(R.id.volume_end);
              String volumeEndText = "" + settings.getVolumeEndPercent();
              volumeEnd.setText(volumeEndText );
              final EditText volumeDuration = (EditText) fadeView.findViewById(R.id.volume_duration);
              String volumeDurationText = "" + settings.getVolumeChangeTimeSec();
              volumeDuration.setText(volumeDurationText );
              final AlertDialog.Builder fadeBuilder = new AlertDialog.Builder(getActivity());
              fadeBuilder.setTitle(R.string.alarm_fade);
              fadeBuilder.setView(fadeView);
              fadeBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      settings.setVolumeStartPercent(tryParseInt(volumeStart.getText().toString(), 0));
                      settings.setVolumeEndPercent(tryParseInt(volumeEnd.getText().toString(), 100));
                      settings.setVolumeChangeTimeSec(tryParseInt(volumeDuration.getText().toString(), 20));
                      settingsAdapter.notifyDataSetChanged();
                      dismiss();
                  }
              });
              fadeBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      dismiss();
                  }
              });
              return fadeBuilder.create();

          case DELETE_CONFIRM:
              final AlertDialog.Builder deleteConfirmBuilder = new AlertDialog.Builder(getActivity());
              deleteConfirmBuilder.setTitle(R.string.delete);
              deleteConfirmBuilder.setMessage(R.string.confirm_delete);
              deleteConfirmBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      service.deleteAlarm(alarmId);
                      dismiss();
                      getActivity().finish();
                  }
              });
              deleteConfirmBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                      dismiss();
                  }
              });
              return deleteConfirmBuilder.create();
        default:
          return super.onCreateDialog(savedInstanceState);
      }
    }

  }
}
