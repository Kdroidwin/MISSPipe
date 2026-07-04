import pytest
from ..missav_api import Client



@pytest.mark.asyncio
async def test_video_attributes():
    client = Client()
    video = await client.get_video("https://missav.ws/dm13/de/fc2-ppv-2777644")

    assert isinstance(video.title, str)
    assert isinstance(video.publish_date, str)
    assert isinstance(video.m3u8_base_url, str)
    assert isinstance(video.video_code, str)
    assert isinstance(video.thumbnail, str)

    search = client.search("stepdaughter", video_count=10)
    async for video in search:
        assert isinstance(video.title, str)

