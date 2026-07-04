<h1 align="center">missAV API</h1> 

<div align="center">
    <a href="https://pepy.tech/project/missAV_api"><img src="https://static.pepy.tech/badge/missAV_api" alt="Downloads"></a>
    <a href="https://github.com/EchterAlsFake/missAV_api/workflows/CodeQL/badge.svg" alt="CodeQL Analysis"></a>
    <a href="https://echteralsfake.me/ci/missAV_api/badge.svg"><img src="https://echteralsfake.me/ci/missAV_api/badge.svg" alt="Sync API Tests"/></a>
    </div>

# Disclaimer
> [!IMPORTANT]
> This is an unofficial and unaffiliated project. Please read the full disclaimer before use:
> **[DISCLAIMER.md](./DISCLAIMER.md)**
>
> By using this project you agree to comply with the target site’s rules, copyright/licensing requirements,
> and applicable laws. Do not use it to bypass access controls or scrape at disruptive rates.

# Features
- Asynchronous
- Fetch videos + metadata
- Download videos
- Search for videos
- Built-in caching
- Easy interface
- Great type hinting

#### Networking Features
- HTTP 2.0 / HTTP 3.0
- Browser impersonation
- Custom JA3
- All proxy types
- Proxy authentication
- Speed Limit
- DNS over HTTPS
- And even more...
- All of this is configurable and can be adjusted as you like!

# Supported Platforms
This API has been tested and confirmed working on:

- Windows 11 (x64) 
- macOS Sequoia (x86_64)
- Linux (Arch) (x86_64)
- Android 16 (aarch64)


# Quickstart

### Have a look at the [Documentation](https://github.com/EchterAlsFake/API_Docs/blob/master/Porn_APIs/missAV.md) for more details

- Install the library with `pip install missAV_api`


```python
from missav_api import Client
# Initialize a Client object

async def do_something():
    client = Client()    
    # Fetch a video
    video_object = await client.get_video("<insert_url_here>")
    
    # Information from Video objects
    print(video_object.title)
    # Download the video
    
    await video_object.download(quality="best", path="your_output_path + filename")

# SEE DOCUMENTATION FOR MORE
```

# Changelog
See [Changelog](https://github.com/EchterAlsFake/missAV_api/blob/master/README/Changelog.md) for more details.

# Contribution
Do you see any issues or having some feature requests? Simply open an Issue or talk
in the discussions.

Pull requests are also welcome.

# License
Licensed under the [LGPLv3](https://www.gnu.org/licenses/lgpl-3.0.en.html) License
<br>Copyright (C) 2024-2026 Johannes Habel
