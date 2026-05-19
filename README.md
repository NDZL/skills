# SKILLS! (BETA, and subject to changes)

- Beta Skills are distributed in a controlled way, through a private repo, by invitation only.
- Open a new Issue on this repo to notify me of your interest and I'll get back to you.

## Wired-Workstation Connect

[https://github.com/NDZL/skills/tree/main/workstation-connect/zwc-config](https://github.com/NDZL/skills-private-distri/tree/main/workstation-connect/zwc-config)

### Prompt example
```
using the attached skill file, generate a JSON configuration for WSC that prevents users to take screenshots, and links this app com.zebra.licenseuiapp/.ui.launch.LaunchActivity on the desktop
```

#### GEMINI INPUT

<img width="630" height="292" alt="image" src="https://github.com/user-attachments/assets/c0018a53-a856-4c92-ac5a-9a1e4e8ffb8a" />


#### GEMINI OUTPUT

<img width="736" height="744" alt="image" src="https://github.com/user-attachments/assets/187ddeda-36ca-41de-af48-45a3a8989dd0" />

#### Fooling AI(!)
<img width="809" height="293" alt="image" src="https://github.com/user-attachments/assets/453533bd-4c23-45e2-bd4a-c8cf559f041c" />



## Hints

### FILES

As a general rule of thumb, don't rely on local files. Progressively higher restrictions are being applied to information sharing through files.

If you can't avoid files, work with public folders like `/data/local/tmp/`

Here is how to drop a file in a public folder (e.g. copying a wallpaper image and changing reading permissions)

```
   cp /sdcard/wallpmay7.png /data/local/tmp/wallpmay7.png
   chmod 644 /data/local/tmp/wallpmay7.png
```

### APPS' LINKS
Embedding apps'links requires the app's package name knowledge and its main activity reference.

To get such information, this is the workflow
- list all the device's packages: `adb shell pm list packages > allPackages.txt`. The result is saved to a file.
- review the generated file, searching for the target app's package and take note of it. E.g. 'com.zebra.licenseuiapp'
- generate the _launch intent_ for that package name: `adb shell "cmd package resolve-activity --brief com.zebra.licenseuiapp | tail -n 1"`
- take note of the resulting string. e.g. `com.zebra.licenseuiapp/.ui.launch.LaunchActivity`
- use that string in the prompt.
  `create a JSON configuration file [...]  that places a link to com.zebra.licenseuiapp/.ui.launch.LaunchActivity on the desktop`
- genAI and the skill will generate the proper restriction. i.e.
  ```
  "key": "shortcutImportFile",
                        "valueString": "[{\"addedDatetime\":1778345710000,\"appInfo\":{\"activityName\":\"com.zebra.licenseuiapp.ui.launch.LaunchActivity\",\"iconImageString\":null,\"imagePath\":null,\"packageName\":\"com.zebra.licenseuiapp\"},\"groupInfo\":null,\"id\":\"sc2026.05.07.17:35:10.a1b2c3\",\"modifiedDatetime\":0,\"shortcutInfo\":null,\"type\":\"application\"}]"
  ```
