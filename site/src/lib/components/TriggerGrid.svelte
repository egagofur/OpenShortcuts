<script lang="ts">
	import Tile from './Tile.svelte';
	import { reveal } from '#lib/reveal.ts';
	import type { Tint } from '#lib/site.ts';

	const groups: { title: string; items: [string, string, Tint][] }[] = [
		{ title: 'Time & place', items: [['Time of day', 'clock', 'blue'], ['Sunrise & sunset', 'sun', 'orange'], ['Alarm', 'alarm', 'orange'], ['Arrive or leave', 'pin', 'red']] },
		{ title: 'Apps & messages', items: [['App opened', 'layers', 'indigo'], ['Notification', 'bell', 'red'], ['WhatsApp message', 'quote', 'green'], ['Long WhatsApp call', 'phone', 'green']] },
		{ title: 'Connections', items: [['Wi-Fi', 'wifi', 'blue'], ['Bluetooth', 'bluetooth', 'blue'], ['Headphones', 'headphones', 'pink'], ['Airplane Mode', 'airplane', 'orange']] },
		{ title: 'Power & focus', items: [['Charger', 'bolt', 'green'], ['Battery level', 'battery', 'green'], ['Battery Saver', 'battery', 'yellow'], ['Do Not Disturb', 'moon', 'indigo']] }
	];
</script>

<div class="grid">
	{#each groups as g, gi (g.title)}
		<div class="group card" use:reveal={gi * 60}>
			<h3>{g.title}</h3>
			<ul>
				{#each g.items as [name, glyph, tint] (name)}
					<li><Tile {glyph} {tint} size={32} /><span>{name}</span></li>
				{/each}
			</ul>
		</div>
	{/each}
</div>

<style>
	.grid {
		display: grid;
		grid-template-columns: repeat(4, minmax(0, 1fr));
		gap: 16px;
		margin-top: 40px;
	}
	.group {
		padding: 20px;
	}
	h3 {
		font-size: 15px;
		font-weight: 600;
		color: var(--ink2);
		letter-spacing: 0;
		margin-bottom: 12px;
	}
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 12px;
	}
	li {
		display: flex;
		align-items: center;
		gap: 12px;
		font-size: 16px;
		font-weight: 500;
		min-width: 0;
	}
	@media (max-width: 960px) {
		.grid {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}
	@media (max-width: 520px) {
		.grid {
			grid-template-columns: 1fr;
		}
	}
</style>
