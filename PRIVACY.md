# Privacy

Field Kit keeps everything on the phone and sends nothing anywhere. It has no internet
permission, so it cannot.

It uses location for two things: the compass's true north, and the position shown on the
compass and on "Calling for help". Location runs only while one of those screens is open (or
the Call for help stage and the SOAP note in "Patient assessment"), and the position is kept only in memory,
except in a note you start. It leaves the phone only when you press Share, to the app you
choose.

"Patient assessment" writes a SOAP note as you answer: your answers, what you write in, the
vital signs and the position, each with its time. It is kept in the app's private storage,
never backed up, until you press "End assessment" and choose to discard it. If you choose "Keep the
note" it stays in the same private storage as the last note, until you delete it or keep another over it.
While an assessment is open the app sets one alarm for the next reassessment, which buzzes (as an
alarm, so it is felt with the app in the background) and shows a notification, and with the screen
off or locked a whole screen that stays until you answer it. The notification can be swiped away;
ending the assessment cancels the alarm and the notification. After a restart or an update the
alarm of an open assessment is set again, which is what the permission to know the phone has
started is for.

The "In case of emergency" card is kept in the app's private storage and is never backed up by
the app. If you switch on "Show on the lock screen", the lines you choose are handed to Glance
(com.wanderwildwood.hitome) on the same phone, which draws them on the lock screen, where
anyone holding the phone can read them without unlocking it. Field Kit answers no other app
that asks. It is off until you switch it on.

## What it asks for

`app/src/main/AndroidManifest.xml` declares:

```
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_COARSE_LOCATION
android.permission.VIBRATE
android.permission.SCHEDULE_EXACT_ALARM
android.permission.USE_FULL_SCREEN_INTENT
android.permission.RECEIVE_BOOT_COMPLETED
```

The last four are for "Patient assessment": a buzz at the end of a 15-second count, the
reassessment reminder (the exact alarm, and the screen it may show over the lock screen), and
setting that alarm again after a restart. Nothing else. Picking an emergency contact uses the phone's own contact picker, which hands
over the one number you pick; Field Kit has no access to your contacts beyond that.

## What it hands to other apps, and only when you press

- Share on a page, a knot, the card, your position or the note: the text, to the app you choose.
- Open in map: a `geo:` address, to your map app.
- Dial: the number, to the dialler, which waits for you to press call.
- Fill in from Medicine: Medicine hands its list to Field Kit, on your say in Medicine.

## Reading the phone's country

To put 112 or 911 first on "Calling for help", the app reads which country the phone's network
or SIM says it is in, then the language setting. It does not use the number, the location or
any permission, and the answer is not kept or sent.

## In the app: Privacy, Terms and the first-aid disclaimer

About holds the same words as the app's Privacy and Terms, and this: "This is a reference for
first aid, not training and not a substitute for emergency services. In an emergency, call
your local emergency number first."
