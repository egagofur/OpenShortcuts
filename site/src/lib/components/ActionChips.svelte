<script lang="ts">
	import Tile from './Tile.svelte';
	import { reveal } from '#lib/reveal.ts';
	import type { Tint } from '#lib/site.ts';

	const groups: { title: string; glyph: string; tint: Tint; items: string[] }[] = [
		{ title: 'Calls & WhatsApp', glyph: 'phoneOff', tint: 'green', items: ['End WhatsApp call', 'End phone call', 'Reply on WhatsApp', 'Send WhatsApp', 'Call', 'Send SMS', 'Email', 'Share'] },
		{ title: 'Device', glyph: 'moon', tint: 'indigo', items: ['Do Not Disturb', 'Ringer', 'Volume', 'Brightness', 'Flashlight', 'Play / pause', 'Vibrate', 'Lock screen', 'Speak text'] },
		{ title: 'Clock & calendar', glyph: 'calendar', tint: 'red', items: ['Timer', 'Alarm', 'Add event', 'Upcoming events', 'Current location'] },
		{ title: 'Scripting', glyph: 'branch', tint: 'gray', items: ['If / Otherwise', 'Repeat', 'Repeat with each', 'Choose from menu', 'Ask for input', 'Variables', 'Wait', 'Run shortcut'] },
		{ title: 'Text & data', glyph: 'text', tint: 'yellow', items: ['Replace', 'Split', 'Combine', 'Match', 'Change case', 'Extract links', 'Lists', 'Dictionaries', 'Calculate', 'Date math'] }
	];
</script>

<div class="groups">
	{#each groups as g, gi (g.title)}
		<div class="group" use:reveal={gi * 50}>
			<div class="head"><Tile glyph={g.glyph} tint={g.tint} size={28} /><h3>{g.title}</h3></div>
			<ul>
				{#each g.items as it (it)}<li>{it}</li>{/each}
			</ul>
		</div>
	{/each}
</div>

<style>
	.groups {
		display: grid;
		gap: 24px;
		margin-top: 32px;
	}
	.head {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	h3 {
		font-size: 1rem;
		font-weight: 600;
		letter-spacing: 0;
	}
	ul {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
		list-style: none;
		margin: 12px 0 0;
		padding: 0;
	}
	li {
		padding: 7px 14px;
		border-radius: var(--radius-pill);
		background: var(--card);
		box-shadow: var(--shadow);
		font-size: 0.9375rem;
		font-weight: 500;
	}
</style>
