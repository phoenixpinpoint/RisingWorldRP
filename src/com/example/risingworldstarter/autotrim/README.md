# Automatic window trim

This package implements CivicCore's automatic wall trimming for windows.

When a player places a window into a solid block wall, `WindowTrimService`
removes only the block cells intersecting the window opening. The frame remains
in place, and surrounding wall blocks are left unchanged.

The carve follows the placed frame's horizontal rotation and uses Rising
World's solid-terrain restriction. This prevents the service from modifying
air, water, or unrelated non-block world elements.

The service is initialized by `CivicCore` and receives CivicCore's debug logger
as a callback. Window placement events are forwarded to the service from the
main plugin listener.
