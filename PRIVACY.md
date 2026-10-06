# Privacy

Field Kit keeps everything on the phone and sends nothing anywhere. It has no internet
permission, so it cannot.

It uses location for two things: the compass's true north, and the position shown on the
compass and on "Calling for help". Location runs only while one of those two screens is open,
and the position is kept only in memory. It leaves the phone only when you press Share
position, to the app you choose.

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
```

Nothing else. Picking an emergency contact uses the phone's own contact picker, which hands
over the one number you pick; Field Kit has no access to your contacts beyond that.

## What it hands to other apps, and only when you press

- Share on a page, a knot, the card or your position: the text, to the app you choose.
- Open in map: a `geo:` address, to your map app.
- Dial: the number, to the dialler, which waits for you to press call.
- Fill in from Medicine: Medicine hands its list to Field Kit, on your say in Medicine.
