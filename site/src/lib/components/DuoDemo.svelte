<script lang="ts">
	import { onMount } from 'svelte';
	import Glyph from './Glyph.svelte';
	import Tile from './Tile.svelte';
	import type { Tint } from '#lib/site.ts';

	type Auto = { id: string; glyph: string; tint: Tint; when: string; run: string; on: boolean; next: string };
	type Phase = 'idle' | 'calling' | 'ended' | 'missed';

	let autos = $state<Auto[]>([
		{ id: 'call', glyph: 'phone', tint: 'green', when: 'WhatsApp call over 45 min, 23:00\u2060–\u206005:00', run: 'Sleep Mode', on: true, next: 'Tonight, when a call runs long' },
		{ id: 'sunset', glyph: 'sun', tint: 'orange', when: '15 min before sunset, daily', run: 'Warm Lights', on: true, next: 'Today at 17:42' },
		{ id: 'car', glyph: 'bluetooth', tint: 'blue', when: 'When My Car connects', run: 'Drive Mode', on: true, next: 'Next time it connects' },
		{ id: 'home', glyph: 'pin', tint: 'red', when: 'When I arrive at Home', run: 'Good Evening', on: false, next: 'Off' }
	]);
	const shortcuts: { name: string; glyph: string; tint: Tint }[] = [
		{ name: 'Sleep Mode', glyph: 'moon', tint: 'indigo' },
		{ name: 'Hang Up WhatsApp', glyph: 'phoneOff', tint: 'green' },
		{ name: 'Good Morning', glyph: 'sun', tint: 'yellow' },
		{ name: 'Battery Check', glyph: 'battery', tint: 'orange' },
		{ name: 'Pomodoro', glyph: 'timer', tint: 'red' },
		{ name: 'Drive Mode', glyph: 'bluetooth', tint: 'blue' }
	];

	let tab = $state<'shortcuts' | 'automation'>('automation');
	let open = $state<string | null>('call');
	let phase = $state<Phase>('idle');
	let callSec = $state(0);
	let now = $state(new Date());
	let battery = $state(65);
	let charging = $state(false);
	let ringShown = $state(false);
	let root: HTMLElement;

	const LIMIT = 45 * 60;
	const still = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

	let clock = $derived(
		phase === 'idle'
			? now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
			: '23:47'
	);
	let callLabel = $derived(
		`${String(Math.floor(callSec / 60)).padStart(2, '0')}:${String(Math.floor(callSec % 60)).padStart(2, '0')}`
	);
	let callAuto = $derived(autos[0]);
	let dimmed = $derived(phase === 'ended');

	let raf = 0;

	function reset() {
		cancelAnimationFrame(raf);
		phase = 'idle';
		callSec = 0;
	}

	function simulate() {
		reset();
		tab = 'automation';
		open = 'call';
		phase = 'calling';
		const from = 44 * 60 + 40;
		const dur = still() ? 0 : 2600;
		const start = performance.now();
		const tick = (t: number) => {
			const p = dur === 0 ? 1 : Math.min(1, (t - start) / dur);
			const eased = 1 - Math.pow(1 - p, 2);
			const target = callAuto.on ? LIMIT : LIMIT + 40;
			callSec = from + (target - from) * eased;
			if (p < 1) raf = requestAnimationFrame(tick);
			else if (callAuto.on) {
				phase = 'ended';
				navigator.vibrate?.(20);
			} else {
				phase = 'missed';
			}
		};
		raf = requestAnimationFrame(tick);
	}

	function toggle(a: Auto) {
		a.on = !a.on;
		if (a.id === 'call' && phase !== 'idle') reset();
	}

	onMount(() => {
		const iv = setInterval(() => (now = new Date()), 15_000);
		const nav = navigator as Navigator & { getBattery?: () => Promise<{ level: number; charging: boolean }> };
		nav.getBattery?.().then((b) => {
			battery = Math.round(b.level * 100);
			charging = b.charging;
		});
		const io = new IntersectionObserver(([e]) => {
			if (e.isIntersecting) {
				ringShown = true;
				io.disconnect();
			}
		}, { threshold: 0.4 });
		io.observe(root);
		return () => {
			clearInterval(iv);
			io.disconnect();
			reset();
		};
	});

	const R = 15;
	const C = 2 * Math.PI * R;
</script>

<div class="demo" bind:this={root}>
	<div class="device" class:dimmed>
		<div class="screen">
			<div class="main">
				{#if phase !== 'idle'}
					<div class="banner {phase}" role="status" aria-live="polite">
						<Tile glyph={phase === 'ended' ? 'phoneOff' : 'phone'} tint={phase === 'ended' ? 'red' : 'green'} size={26} />
						<span class="who">{phase === 'ended' ? 'Call ended by Sleep Mode' : 'WhatsApp call · Mom'}</span>
						<span class="time">{callLabel}</span>
					</div>
				{/if}

				<header>
					<h3>{tab === 'automation' ? 'Automation' : 'All Shortcuts'}</h3>
					<p>{tab === 'automation' ? 'Runs shortcuts for you, even while you sleep' : `${shortcuts.length} shortcuts`}</p>
				</header>

				<div class="pane">
					{#if tab === 'automation'}
						<ul class="list">
							{#each autos as a (a.id)}
								<li class="auto" class:off={!a.on} class:open={open === a.id} class:fired={a.id === 'call' && phase === 'ended'}>
									<button type="button" class="row" aria-expanded={open === a.id} onclick={() => (open = open === a.id ? null : a.id)}>
										<Tile glyph={a.glyph} tint={a.tint} size={34} />
										<span class="txt">
											<span class="when">{a.when}</span>
											<span class="run">Run <b>{a.run}</b></span>
										</span>
									</button>
									<label class="switch">
										<input type="checkbox" checked={a.on} onchange={() => toggle(a)} aria-label="{a.on ? 'Turn off' : 'Turn on'}: {a.when}" />
										<span class="track"><span class="knob"></span></span>
									</label>
									<div class="more" aria-hidden={open !== a.id}>
										<div>
											<p>{a.on ? `Next run: ${a.next}` : 'This automation is off.'}</p>
										</div>
									</div>
								</li>
							{/each}
						</ul>
					{:else}
						<ul class="tiles">
							{#each shortcuts as s, i (s.name)}
								<li style:--i={i} style:--t="var(--tint-{s.tint})">
									<button type="button"><Glyph name={s.glyph} size={20} /><span>{s.name}</span></button>
								</li>
							{/each}
						</ul>
					{/if}
				</div>

				<div class="tabs" role="tablist" aria-label="Sections">
					<span class="pill" class:right={tab === 'automation'}></span>
					<button type="button" role="tab" aria-selected={tab === 'shortcuts'} onclick={() => (tab = 'shortcuts')}>Shortcuts</button>
					<button type="button" role="tab" aria-selected={tab === 'automation'} onclick={() => (tab = 'automation')}>Automation</button>
				</div>
			</div>

			<aside class="rail">
				<span class="home"><Glyph name="home" size={16} /></span>
				<span class="clock">{clock}</span>
				<button type="button" class="ring" onclick={() => (charging = !charging)} aria-label="Battery {battery}%{charging ? ', charging' : ''}">
					<svg viewBox="0 0 36 36" width="40" height="40">
						<circle cx="18" cy="18" r={R} class="bg" />
						<circle
							cx="18"
							cy="18"
							r={R}
							class="fg"
							stroke-dasharray={C}
							stroke-dashoffset={ringShown ? C * (1 - battery / 100) : C}
						/>
					</svg>
					<span class="pct">{#if charging}<Glyph name="boltFill" size={9} />{/if}{battery}</span>
				</button>
				{#if phase === 'ended'}<span class="dnd" aria-label="Do Not Disturb on"><Glyph name="moon" size={16} /></span>{/if}
			</aside>

			{#if phase === 'ended'}
				<div class="toast" role="status">
					<Tile glyph="moon" tint="indigo" size={30} />
					<span><b>Sleep Mode ran</b><br />Call ended · Do Not Disturb on · Brightness 10%</span>
				</div>
			{/if}
		</div>
	</div>

	<div class="controls">
		<button type="button" class="sim" onclick={phase === 'idle' ? simulate : reset}>
			<Glyph name={phase === 'idle' ? 'playFill' : 'undo'} size={16} />
			{phase === 'idle' ? 'Simulate a late call' : 'Reset'}
		</button>
		<p class="note" aria-live="polite">
			{#if phase === 'idle'}
				{callAuto.on ? 'Fast-forwards a WhatsApp call to 45 minutes. Try it, then switch the automation off and try again.' : 'The call automation is off. Simulate to see what happens without it.'}
			{:else if phase === 'calling'}
				It’s 23:47 and the call is still going…
			{:else if phase === 'ended'}
				Sleep Mode pressed Hang up, turned on Do Not Disturb and dimmed the screen.
			{:else}
				With the automation off, the call keeps going and your battery keeps draining.
			{/if}
		</p>
	</div>
</div>

<style>
	.demo {
		margin-top: 40px;
		container-type: inline-size;
	}
	.device {
		--screen: #0a0a0c;
		--cell: #1c1c1f;
		--cell2: #26262a;
		--text: #f3f3f6;
		--text2: #9e9ea6;
		--blue: #5aa2ff;
		position: relative;
		padding: 10px;
		border-radius: 46px;
		background: #1a1a1d;
		box-shadow:
			inset 0 0 0 1.5px #3a3a40,
			0 40px 80px -30px rgb(0 0 0 / 0.45);
		max-width: 980px;
		margin-inline: auto;
	}
	.screen {
		position: relative;
		display: grid;
		grid-template-columns: minmax(0, 1fr) 76px;
		aspect-ratio: 2.15;
		border-radius: 36px;
		background: var(--screen);
		color: var(--text);
		overflow: hidden;
		isolation: isolate;
	}
	.screen::after {
		content: '';
		position: absolute;
		inset: 0;
		background: black;
		opacity: 0;
		pointer-events: none;
		transition: opacity 900ms var(--ease-out);
		z-index: 5;
	}
	.dimmed .screen::after {
		opacity: 0.4;
	}

	.main {
		position: relative;
		display: grid;
		grid-template-rows: auto minmax(0, 1fr) auto;
		padding: 22px 8px 16px 26px;
		min-height: 0;
	}
	header h3 {
		font-size: 1.625rem;
		font-weight: 700;
		letter-spacing: -0.02em;
	}
	header p {
		font-size: 0.8125rem;
		color: var(--text2);
		margin-top: 2px;
	}
	.pane {
		min-height: 0;
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: none;
		margin-top: 14px;
		padding-right: 10px;
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 10px;
		align-items: start;
	}
	.auto {
		position: relative;
		border-radius: 22px;
		background: var(--cell);
		transition:
			opacity 250ms var(--ease-out),
			box-shadow 250ms var(--ease-out);
	}
	.auto.off {
		opacity: 0.55;
	}
	.auto.fired {
		box-shadow: 0 0 0 2px var(--tint-indigo);
		animation: pulse 900ms var(--ease-out);
	}
	.row {
		display: flex;
		align-items: flex-start;
		gap: 12px;
		width: 100%;
		padding: 14px 62px 14px 14px;
		border: 0;
		background: none;
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
		border-radius: 22px;
		transition: scale 160ms var(--ease-out);
		touch-action: manipulation;
	}
	.row:active {
		scale: 0.98;
	}
	.txt {
		display: grid;
		gap: 2px;
		min-width: 0;
	}
	.when {
		font-size: 0.9375rem;
		font-weight: 600;
		line-height: 1.3;
	}
	.run {
		font-size: 0.8125rem;
		color: var(--text2);
	}
	.run b {
		color: var(--blue);
		font-weight: 500;
	}
	.more {
		display: grid;
		grid-template-rows: 0fr;
		transition: grid-template-rows 280ms var(--ease-out);
	}
	.more > div {
		overflow: hidden;
	}
	.open .more {
		grid-template-rows: 1fr;
	}
	.more p {
		margin: -4px 14px 14px 60px;
		font-size: 0.8125rem;
		color: var(--text2);
	}

	.switch {
		position: absolute;
		top: 16px;
		right: 14px;
		cursor: pointer;
	}
	.switch input {
		position: absolute;
		opacity: 0;
		inset: -10px;
		width: auto;
		height: auto;
		margin: 0;
		cursor: pointer;
	}
	.track {
		display: block;
		width: 40px;
		height: 24px;
		border-radius: 12px;
		background: #3a3a40;
		padding: 2px;
		transition: background-color 250ms var(--ease-out);
	}
	.knob {
		display: block;
		width: 20px;
		height: 20px;
		border-radius: 10px;
		background: white;
		box-shadow: 0 2px 4px rgb(0 0 0 / 0.25);
		transition:
			translate 280ms cubic-bezier(0.3, 1.4, 0.5, 1),
			width 160ms var(--ease-out);
	}
	.switch input:checked + .track {
		background: var(--tint-green);
	}
	.switch input:checked + .track .knob {
		translate: 16px 0;
	}
	.switch input:active + .track .knob {
		width: 25px;
	}
	.switch input:checked:active + .track .knob {
		translate: 11px 0;
	}
	.switch input:focus-visible + .track {
		outline: 3px solid var(--blue);
		outline-offset: 2px;
	}

	.tiles {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 10px;
	}
	.tiles li {
		animation: rise 360ms var(--ease-out) backwards;
		animation-delay: calc(var(--i) * 30ms);
	}
	.tiles button {
		display: grid;
		align-content: space-between;
		gap: 18px;
		width: 100%;
		min-height: 92px;
		padding: 14px;
		border: 0;
		border-radius: 22px;
		background: var(--t);
		color: white;
		font: inherit;
		font-size: 0.9375rem;
		font-weight: 600;
		text-align: left;
		cursor: pointer;
		transition: scale 160ms var(--ease-out);
	}
	.tiles button:active {
		scale: 0.96;
	}

	.tabs {
		position: relative;
		justify-self: start;
		display: grid;
		grid-template-columns: 1fr 1fr;
		margin-top: 12px;
		padding: 4px;
		border-radius: 999px;
		background: var(--cell);
	}
	.tabs button {
		position: relative;
		z-index: 1;
		min-height: 36px;
		padding: 0 18px;
		border: 0;
		background: none;
		color: var(--text2);
		font: inherit;
		font-size: 0.875rem;
		font-weight: 600;
		cursor: pointer;
		transition: color 200ms var(--ease-out);
	}
	.tabs button[aria-selected='true'] {
		color: var(--text);
	}
	.pill {
		position: absolute;
		top: 4px;
		bottom: 4px;
		left: 4px;
		width: calc(50% - 4px);
		border-radius: 999px;
		background: var(--cell2);
		transition: translate 320ms cubic-bezier(0.32, 0.72, 0, 1);
	}
	.pill.right {
		translate: 100% 0;
	}

	.rail {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12px;
		padding-top: 22px;
		color: var(--text2);
	}
	.home {
		color: var(--blue);
	}
	.clock {
		font-size: 1.0625rem;
		font-weight: 600;
		color: var(--text);
		font-variant-numeric: tabular-nums;
		letter-spacing: -0.01em;
	}
	.ring {
		position: relative;
		display: grid;
		place-items: center;
		padding: 2px;
		border: 0;
		background: none;
		color: var(--text);
		cursor: pointer;
		border-radius: 50%;
		transition: scale 160ms var(--ease-out);
	}
	.ring:active {
		scale: 0.92;
	}
	.ring svg {
		rotate: -90deg;
	}
	.ring .bg {
		fill: none;
		stroke: #2e2e33;
		stroke-width: 3;
	}
	.ring .fg {
		fill: none;
		stroke: var(--tint-green);
		stroke-width: 3;
		stroke-linecap: round;
		transition: stroke-dashoffset 1200ms var(--ease-out);
	}
	.pct {
		position: absolute;
		display: flex;
		align-items: center;
		font-size: 0.6875rem;
		font-weight: 600;
		font-variant-numeric: tabular-nums;
	}
	.dnd {
		color: var(--tint-indigo);
		animation: rise 400ms var(--ease-out);
	}

	.banner {
		position: absolute;
		top: 12px;
		left: 50%;
		z-index: 6;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 6px 14px 6px 6px;
		border-radius: 999px;
		background: #000;
		box-shadow: 0 0 0 1px #2e2e33;
		translate: -50% 0;
		font-size: 0.8125rem;
		font-weight: 600;
		white-space: nowrap;
		animation: drop 420ms var(--ease-out);
	}
	.banner .time {
		font-variant-numeric: tabular-nums;
		color: var(--tint-green);
		min-width: 3.2em;
		text-align: right;
	}
	.banner.ended .time {
		color: var(--tint-red);
	}
	.banner.missed .time {
		color: var(--tint-orange);
	}
	.toast {
		position: absolute;
		right: 92px;
		bottom: 16px;
		z-index: 6;
		display: flex;
		align-items: center;
		gap: 10px;
		max-width: 320px;
		padding: 10px 14px 10px 10px;
		border-radius: 20px;
		background: rgb(38 38 42 / 0.86);
		backdrop-filter: blur(16px);
		-webkit-backdrop-filter: blur(16px);
		font-size: 0.8125rem;
		line-height: 1.35;
		color: var(--text2);
		animation: toast 460ms 250ms var(--ease-out) backwards;
	}
	.toast b {
		color: var(--text);
	}

	.controls {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: center;
		gap: 12px 18px;
		margin-top: 22px;
		text-align: left;
	}
	.sim {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		min-height: 44px;
		padding: 0 20px;
		border: 0;
		border-radius: 999px;
		background: var(--ink);
		color: var(--bg);
		font: inherit;
		font-size: 0.9375rem;
		font-weight: 600;
		cursor: pointer;
		transition: scale 160ms var(--ease-out);
		touch-action: manipulation;
	}
	.sim:active {
		scale: 0.97;
	}
	.note {
		max-width: 30em;
		font-size: 0.9375rem;
		color: var(--ink2);
	}

	@keyframes drop {
		from {
			opacity: 0;
			translate: -50% -10px;
		}
	}
	@keyframes toast {
		from {
			opacity: 0;
			translate: 0 10px;
		}
	}
	@keyframes rise {
		from {
			opacity: 0;
			translate: 0 6px;
		}
	}
	@keyframes pulse {
		0% {
			box-shadow: 0 0 0 0 var(--tint-indigo);
		}
		60% {
			box-shadow: 0 0 0 8px rgb(86 84 212 / 0);
		}
	}

	@container (max-width: 760px) {
		.device {
			max-width: 400px;
			border-radius: 44px;
		}
		.screen {
			aspect-ratio: auto;
			height: 640px;
			grid-template-columns: 1fr;
			grid-template-rows: auto minmax(0, 1fr);
		}
		.rail {
			order: -1;
			flex-direction: row;
			justify-content: flex-end;
			padding: 14px 20px 0;
		}
		.home {
			margin-right: auto;
		}
		.main {
			padding: 6px 12px 14px 18px;
		}
		.list,
		.tiles {
			grid-template-columns: minmax(0, 1fr);
		}
		.tiles {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
		.banner {
			top: 52px;
		}
		.toast {
			left: 14px;
			right: 14px;
			bottom: 70px;
			max-width: none;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.knob,
		.pill,
		.ring .fg,
		.more {
			transition-duration: 0ms;
		}
		.auto.fired,
		.banner,
		.toast,
		.tiles li,
		.dnd {
			animation: fade 200ms ease;
		}
		@keyframes fade {
			from {
				opacity: 0;
			}
		}
	}
</style>
