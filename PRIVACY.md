# Privacy

Field Kit keeps everything on the phone and sends nothing anywhere. It has no internet
permission, so it cannot.

It uses location for two things: the compass's true north, and the position shown on the
compass and on "Calling for help". Location runs only while one of those screens is open (or
the Call stage and the note in "Work through it"), and the position is kept only in memory,
except in a note you start. It leaves the phone only when you press Share, to the app you
choose.

"Work through it" writes a note as you answer: your answers, what you write in, the vitals and
the position, each with its time. It is kept in the app's private storage, never backed up,
until you press "Start over" twice. While a note is open the app sets one alarm for the next
recheck, which buzzes and shows a notification.

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
```

The last two are for "Work through it": a buzz at the end of a 15-second count, and the recheck
reminder. Nothing else. Picking an emergency contact uses the phone's own contact picker, which hands
over the one number you pick; Field Kit has no access to your contacts beyond that.

## What it hands to other apps, and only when you press

- Share on a page, a knot, the card, your position or the note: the text, to the app you choose.
- Open in map: a `geo:` address, to your map app.
- Dial: the number, to the dialler, which waits for you to press call.
- Fill in from Medicine: Medicine hands its list to Field Kit, on your say in Medicine.
