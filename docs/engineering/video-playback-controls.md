# Video seeking and source selection

The feed player exposes a 44px seek target while retaining a thin track. Pointer movement only changes the time preview; pointer release commits one seek. Cancellation restores the displayed current position. A playing video resumes after `seeked`; a paused video stays paused. Arrow keys move by five seconds and Home/End jump to the bounds. Buffered ranges are displayed independently of the preview.

`GET /api/v1/app/videos/{videoId}/sources` returns ready sources for a public video whose author is active. The list includes the existing optimized rendition and original media, with available stored dimensions. No additional resolution is invented, no new rendition is generated, and no database migration is required. Private or unavailable video sources are not exposed.

The quality menu is fetched on demand. Source changes preserve position, playback rate, mute and play/pause state, wait for metadata/playable data, and fall back to the preceding source if loading fails. Pending listeners/timeouts are removed on cancellation or disposal.

Verification on 2026-10-15: 119 frontend unit tests, six focused backend service tests, three real-video browser tests and eight mixed-content browser regressions passed. Local Docker was updated after a database backup. The public source endpoint and byte-range reads of both available renditions returned HTTP 200/206 successfully. No release ZIP was produced.
