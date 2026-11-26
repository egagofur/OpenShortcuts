<script lang="ts">
	import { onMount, type Snippet } from 'svelte';
	import Glyph from './Glyph.svelte';
	import type { Tint } from '#lib/site.ts';

	// Every size below is the app's dp/sp value (ui/Theme.kt, Kit.kt, HomeScreen.kt, AutomationScreen.kt).
	// The screen is laid out at the Find X8's real size in dp, then scaled to fit the page.
	const SIZE = { land: { w: 789, h: 359 }, port: { w: 359, h: 789 } };
	const INSET = { land: { top: 0, start: 38, bottom: 0 }, port: { top: 34, start: 0, bottom: 20 } };

	type Kind = 'call' | 'time' | 'battery';
	type Auto = {
		id: string;
		kind: Kind;
		title: string;
		glyph: string;
		tint: Tint;
		run: string;
		runGlyph: string;
		runTint: Tint;
		enabled: boolean;
		warnFirst: boolean;
		minutes: number;
		from: string;
		to: string;
		at: string;
		days: string;
		level: number;
		below: boolean;
	};

	let autos = $state<Auto[]>([
		{ id: 'a1', kind: 'call', title: 'WhatsApp Call Runs Long', glyph: 'phone', tint: 'green', run: 'Sleep Mode', runGlyph: 'moon', runTint: 'indigo', enabled: true, warnFirst: false, minutes: 45, from: '23:00', to: '05:00', at: '', days: '', level: 0, below: true },
		{ id: 'a2', kind: 'time', title: 'Time of Day', glyph: 'clock', tint: 'blue', run: 'Good Morning', runGlyph: 'sun', runTint: 'yellow', enabled: true, warnFirst: false, minutes: 0, from: '', to: '', at: '07:00', days: 'Weekdays', level: 0, below: true },
		{ id: 'a3', kind: 'battery', title: 'Battery Level', glyph: 'battery', tint: 'orange', run: 'Battery Check', runGlyph: 'battery', runTint: 'orange', enabled: false, warnFirst: false, minutes: 0, from: '', to: '', at: '', days: '', level: 20, below: true }
	]);
	const shortcuts: { id: string; name: string; glyph: string; tint: Tint }[] = [
		{ id: 's1', name: 'Sleep Mode', glyph: 'moon', tint: 'indigo' },
		{ id: 's2', name: 'Hang Up WhatsApp', glyph: 'phoneOff', tint: 'green' },
		{ id: 's3', name: 'Battery Check', glyph: 'battery', tint: 'orange' },
		{ id: 's4', name: 'Good Morning', glyph: 'sun', tint: 'yellow' }
	];

	function headline(a: Auto) {
		if (a.kind === 'call') return `WhatsApp call over ${a.minutes} min, ${a.from}⁠–⁠${a.to}`;
		if (a.kind === 'time') return `At ${a.at}, ${a.days.toLowerCase()}`;
		return `Battery ${a.below ? 'falls below' : 'rises above'} ${a.level}%`;
	}
	function cycle<T>(list: T[], v: T): T {
		return list[(list.indexOf(v) + 1) % list.length];
	}

	let box: HTMLElement;
	let mode = $state<'land' | 'port'>('land');
	let k = $state(1);
	let dim = $derived(SIZE[mode]);
	let inset = $derived(INSET[mode]);
	let short = $derived(dim.h < 480);

	let tab = $state<0 | 1>(1);
	let shownId = $state<string | null>(null);
	let detail = $derived(autos.find((a) => a.id === shownId) ?? null);
	// Nav.kt: p = 1 means the pushed screen is fully off to the right, 0 means it is on screen.
	let p = $state(1);
	let dragging = $state(false);

	let now = $state(new Date());
	let showDate = $state(false);
	let battery = $state(65);
	let charging = $state(false);
	let sweep = $state(1);
	let running = $state<string | null>(null);
	let done = $state<string | null>(null);

	type Phase = 'idle' | 'calling' | 'ran' | 'missed';
	let phase = $state<Phase>('idle');
	let callSec = $state(0);
	let raf = 0;

	const still = () => typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
	const two = (n: number) => String(n).padStart(2, '0');
	let clock = $derived(phase === 'idle' ? `${two(now.getHours())}:${two(now.getMinutes())}` : '23:47');
	let date = $derived(now.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric' }).replace(',', ''));
	let arc = $derived(charging ? 'var(--positive)' : battery <= 20 ? 'var(--danger)' : 'var(--ink)');
	let callLabel = $derived(`${two(Math.floor(callSec / 60))}:${two(Math.floor(callSec % 60))}`);

	// Critically damped spring (damping ratio 1, stiffness 300), started from the current value and velocity.
	let springRaf = 0;
	let pv = 0;
	function animateTo(target: number, velocity = 0, then?: () => void) {
		cancelAnimationFrame(springRaf);
		if (still()) {
			p = target;
			then?.();
			return;
		}
		pv = velocity;
		let last = performance.now();
		const step = (t: number) => {
			let left = Math.min(1, (t - last) / 1000);
			last = t;
			let x = p;
			while (left > 0) {
				const dt = Math.min(0.004, left);
				pv += (-300 * (x - target) - 2 * Math.sqrt(300) * pv) * dt;
				x += pv * dt;
				left -= dt;
			}
			p = x;
			if (Math.abs(p - target) < 0.0005 && Math.abs(pv) < 0.01) {
				p = target;
				pv = 0;
				then?.();
			} else springRaf = requestAnimationFrame(step);
		};
		springRaf = requestAnimationFrame(step);
	}
	function push(id: string) {
		shownId = id;
		p = 1;
		animateTo(0);
	}
	function pop(velocity = 0) {
		dragging = false;
		animateTo(1, velocity, () => (shownId = null));
	}

	let startX = 0;
	let lastX = 0;
	let lastT = 0;
	let vel = 0;
	let startP = 0;
	function edgeDown(e: PointerEvent) {
		(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
		cancelAnimationFrame(springRaf);
		startX = lastX = e.clientX;
		startP = p;
		lastT = e.timeStamp;
		vel = 0;
		dragging = true;
	}
	function edgeMove(e: PointerEvent) {
		if (!dragging) return;
		vel = (e.clientX - lastX) / k / Math.max(1, e.timeStamp - lastT);
		lastX = e.clientX;
		lastT = e.timeStamp;
		p = Math.min(1, Math.max(0, startP + (e.clientX - startX) / k / dim.w));
	}
	function edgeUp() {
		if (!dragging) return;
		dragging = false;
		const v = (vel * 1000) / dim.w;
		if (p > 0.4 || vel > 0.5) pop(v);
		else animateTo(0, v);
	}

	function runTile(id: string) {
		if (running) return;
		running = id;
		setTimeout(
			() => {
				running = null;
				done = id;
				setTimeout(() => (done = null), 1300);
			},
			still() ? 200 : 1100
		);
	}

	function tapClock() {
		showDate = !showDate;
		if (showDate) setTimeout(() => (showDate = false), 3000);
	}
	function tapBattery() {
		sweep = 0;
		requestAnimationFrame(() => requestAnimationFrame(() => (sweep = 1)));
	}

	function reset() {
		cancelAnimationFrame(raf);
		phase = 'idle';
		callSec = 0;
	}
	function simulate() {
		reset();
		if (shownId) pop();
		tab = 1;
		phase = 'calling';
		const a = autos[0];
		const from = (a.minutes - 1) * 60 + 40;
		const target = a.minutes * 60 + (a.enabled ? 0 : 50);
		const dur = still() ? 0 : 2800;
		const start = performance.now();
		const tick = (t: number) => {
			const p = dur === 0 ? 1 : Math.min(1, (t - start) / dur);
			callSec = from + (target - from) * (1 - (1 - p) * (1 - p));
			if (p < 1) raf = requestAnimationFrame(tick);
			else if (a.enabled) {
				phase = 'ran';
				navigator.vibrate?.(20);
			} else phase = 'missed';
		};
		raf = requestAnimationFrame(tick);
	}

	onMount(() => {
		const ro = new ResizeObserver(([e]) => {
			const w = e.contentRect.width;
			mode = w >= 680 ? 'land' : 'port';
			const s = SIZE[mode];
			k = Math.min((w - 20) / s.w, mode === 'port' ? 700 / s.h : 10);
		});
		ro.observe(box);
		const iv = setInterval(() => (now = new Date()), 10_000);
		const nav = navigator as Navigator & { getBattery?: () => Promise<{ level: number; charging: boolean }> };
		nav.getBattery?.().then((b) => {
			battery = Math.round(b.level * 100);
			charging = b.charging;
		});
		const io = new IntersectionObserver(
			([e]) => {
				if (e.isIntersecting) {
					tapBattery();
					io.disconnect();
				}
			},
			{ threshold: 0.4 }
		);
		io.observe(box);
		return () => {
			ro.disconnect();
			io.disconnect();
			clearInterval(iv);
			cancelAnimationFrame(raf);
			cancelAnimationFrame(springRaf);
		};
	});

	const R = 17.5;
	const C = 2 * Math.PI * R;
</script>

{#snippet rail(atRoot: boolean)}
	<aside class="rail" style:padding-top="{(short ? 8 : 14) + inset.top}px" style:padding-bottom="{(short ? 10 : 16) + inset.bottom}px">
		{#if !short}
			<button type="button" class="home" class:accent={!atRoot} aria-label={atRoot ? 'Home' : 'Go to Home'} onclick={() => !atRoot && pop()}>
				<Glyph name="home" size={17} />
			</button>
		{/if}
		<button type="button" class="clock" onclick={tapClock} aria-label={showDate ? 'Show time' : 'Show date'}>
			{#key showDate}<span class:date={showDate}>{showDate ? date : clock}</span>{/key}
		</button>
		<button type="button" class="battery" onclick={tapBattery} aria-label="Battery {battery}%{charging ? ', charging' : ''}">
			<svg viewBox="0 0 38 38" width="38" height="38">
				<circle cx="19" cy="19" r={R} class="track" />
				<circle cx="19" cy="19" r={R} class="arc" style:stroke={arc} stroke-dasharray={C} stroke-dashoffset={C * (1 - (battery / 100) * sweep)} />
			</svg>
			{#if charging}<span class="bolt"><Glyph name="boltFill" size={10} /></span>{/if}
			<span class="pct" class:down={charging}>{Math.round(battery * sweep)}</span>
		</button>
		<span class="grow"></span>
		<div class="controls" style:gap="{short ? 10 : 14}px">
			{#if atRoot}
				<span class="glass" title="Setup"><Glyph name="sliders" size={24} /></span>
				<span class="glass" title={tab === 0 ? 'New shortcut' : 'New automation'}><Glyph name="plus" size={24} /></span>
			{:else}
				<button type="button" class="glass" aria-label="Back" onclick={() => pop()}><Glyph name="chevronLeft" size={24} /></button>
			{/if}
		</div>
	</aside>
{/snippet}

{#snippet sentence(glyph: string, tint: Tint, children: Snippet, trailing?: Snippet)}
	<div class="card sentence">
		<span class="tile" style:--t="var(--tint-{tint})" style:--s="28px"><Glyph name={glyph} size={18} /></span>
		<div class="flow">{@render children()}</div>
		{#if trailing}{@render trailing()}{/if}
	</div>
{/snippet}

{#snippet sw(on: boolean, label: string, toggle: () => void)}
	<label class="switch">
		<input type="checkbox" checked={on} onchange={toggle} aria-label={label} />
		<span class="track"><span class="thumb"></span></span>
	</label>
{/snippet}

<div class="demo" bind:this={box}>
	<div class="device" style:width="{dim.w * k + 20}px">
		<div class="viewport" style:width="{dim.w * k}px" style:height="{dim.h * k}px">
			<div class="screen" class:dimmed={phase === 'ran'} style:width="{dim.w}px" style:height="{dim.h}px" style:scale={k}>
				<div class="page home-page" style:translate={shownId && !still() ? `${-30 * (1 - p)}% 0` : null}>
					<div class="main">
						<div class="scroll" style:padding="{inset.top}px 4px {short ? 24 : 112}px {20 + inset.start}px">
							{#if tab === 1}
								<header class="head" class:short>
									<h3>Automation</h3>
									<p>Runs shortcuts for you, even while you sleep</p>
								</header>
								<ul class="autos">
									{#each autos as a (a.id)}
										<li class="card auto">
											<button type="button" class="open" aria-label="Edit automation: {headline(a)}" onclick={() => push(a.id)}></button>
											<span class="tile" style:--t="var(--tint-{a.tint})" style:--s="34px"><Glyph name={a.glyph} size={22} /></span>
											<span class="txt">
												<span class="hl">{headline(a)}</span>
												<span class="sub">Run {a.run}</span>
											</span>
											{@render sw(a.enabled, `${a.enabled ? 'Turn off' : 'Turn on'} ${headline(a)}`, () => (a.enabled = !a.enabled))}
										</li>
									{/each}
								</ul>
							{:else}
								<header class="head" class:short>
									<h3>All Shortcuts</h3>
									<p>{shortcuts.length} shortcuts</p>
								</header>
								<ul class="grid">
									{#each shortcuts as s (s.id)}
										<li>
											<button type="button" class="stile" class:running={running === s.id} class:done={done === s.id} style:--t="var(--tint-{s.tint})" aria-label="Run {s.name}" onclick={() => runTile(s.id)}>
												<span class="g">
													{#key running === s.id ? 'r' : done === s.id ? 'd' : 'g'}
														<span class="gi"><Glyph name={running === s.id ? 'timer' : done === s.id ? 'check' : s.glyph} size={26} /></span>
													{/key}
												</span>
												<span class="more"><Glyph name="more" size={18} /></span>
												<span class="nm">{s.name}</span>
											</button>
										</li>
									{/each}
								</ul>
							{/if}
						</div>

						<div
							class="tabs"
							class:short
							role="tablist"
							aria-label="Sections"
							style:--seg-w="{short ? 108 : 124}px"
							style:--seg-h="{short ? 40 : 48}px"
							style:bottom={short ? 'auto' : `${16 + inset.bottom}px`}
							style:top={short ? `${10 + inset.top}px` : 'auto'}
						>
							<span class="pill" style:translate="{tab * (short ? 108 : 124)}px 0"></span>
							<button type="button" role="tab" aria-selected={tab === 0} onclick={() => (tab = 0)}>Shortcuts</button>
							<button type="button" role="tab" aria-selected={tab === 1} onclick={() => (tab = 1)}>Automation</button>
						</div>
					</div>
					{@render rail(true)}
					<div class="scrim" style:opacity={shownId ? 1 - p : 0}></div>
				</div>

				{#if detail}
					{@const a = detail}
					<div class="page detail-page" class:moving={p > 0.0005} style:translate={still() ? null : `${p * 100}% 0`} style:opacity={still() ? 1 - p : null}>
						<div class="main">
							<div class="scroll" style:padding="{inset.top}px 4px {32 + inset.bottom}px {18 + inset.start}px">
								<div class="dhead">
									<span class="kind">
										<span class="tile" style:--t="var(--tint-{a.tint})" style:--s="32px"><Glyph name={a.glyph} size={21} /></span>
										<span class="kt">{a.title}</span>
										<span class="chev"><Glyph name="chevronDown" size={16} /></span>
									</span>
									{@render sw(a.enabled, a.enabled ? 'Turn off this automation' : 'Turn on this automation', () => (a.enabled = !a.enabled))}
								</div>

								{#if a.kind === 'call'}
									{#snippet c1()}<span class="w">When a WhatsApp call lasts</span><button type="button" class="pill-t" onclick={() => (a.minutes = cycle([30, 45, 60, 90], a.minutes))}>{a.minutes} min</button>{/snippet}
									{@render sentence('phone', 'green', c1)}
									<div class="gap"></div>
									{#snippet c2()}<span class="w">Only between</span><button type="button" class="pill-t" onclick={() => (a.from = cycle(['22:00', '23:00', '00:00'], a.from))}>{a.from}</button><span class="w">and</span><button type="button" class="pill-t" onclick={() => (a.to = cycle(['05:00', '06:00', '07:00'], a.to))}>{a.to}</button>{/snippet}
									{@render sentence('moon', 'indigo', c2)}
								{:else if a.kind === 'time'}
									<div class="seg" aria-hidden="true">
										<span class="seg-pill"></span>
										{#each ['Time', 'Sunrise', 'Sunset', 'Interval', 'Once'] as m, i (m)}<span class="seg-o" class:on={i === 0}>{m}</span>{/each}
									</div>
									<div class="gap" style:height="10px"></div>
									{#snippet c3()}<span class="w">At</span><button type="button" class="pill-t" onclick={() => (a.at = cycle(['06:30', '07:00', '07:30'], a.at))}>{a.at}</button><button type="button" class="pill-t" onclick={() => (a.days = cycle(['Weekdays', 'Daily', 'Weekends'], a.days))}>{a.days.toLowerCase()}</button>{/snippet}
									{@render sentence('clock', 'blue', c3)}
									<p class="note">Next run: tomorrow at {a.at}</p>
								{:else}
									{#snippet c4()}<span class="w">When battery</span><button type="button" class="pill-t" onclick={() => (a.below = !a.below)}>{a.below ? 'falls below' : 'rises above'}</button><button type="button" class="pill-t" onclick={() => (a.level = cycle([10, 20, 30, 50], a.level))}>{a.level}%</button>{/snippet}
									{@render sentence('battery', 'orange', c4)}
								{/if}

								<div class="link"><span></span></div>
								{#snippet runSeg()}<span class="w">Run</span><span class="pill-t with"><span class="tile" style:--t="var(--tint-{a.runTint})" style:--s="20px"><Glyph name={a.runGlyph} size={13} /></span>{a.run}</span>{/snippet}
								{#snippet runTrail()}<span class="go"><Glyph name="chevronRight" size={16} /></span>{/snippet}
								{@render sentence('layers', 'purple', runSeg, runTrail)}
								{#if a.kind === 'battery'}<p class="note">The battery percentage goes to the shortcut as its input.</p>{/if}

								<div class="gap"></div>
								{#snippet ask()}<span class="w">Ask before running</span>{/snippet}
								{#snippet askSw()}{@render sw(a.warnFirst, 'Ask before running', () => (a.warnFirst = !a.warnFirst))}{/snippet}
								{@render sentence('bell', 'red', ask, askSw)}
								<p class="note" style:margin-top="10px">
									{#if a.kind === 'call'}
										{a.warnFirst ? 'A notification gives you a minute to snooze or skip. If you fell asleep, the call ends by itself.' : 'Runs by itself, with no notification to approve.'}
									{:else}
										{a.warnFirst ? 'A notification gives you a minute to skip this run or snooze it 30 minutes.' : 'Runs by itself, with no notification to approve.'}
									{/if}
								</p>
								<div class="quiet">Delete Automation</div>
							</div>
							<div class="edge" role="presentation" onpointerdown={edgeDown} onpointermove={edgeMove} onpointerup={edgeUp} onpointercancel={edgeUp}></div>
						</div>
						{@render rail(false)}
					</div>
				{/if}

				{#if phase !== 'idle'}
					<div class="heads" role="status" aria-live="polite" style:top="{inset.top + 8}px">
						{#if phase === 'ran'}
							<span class="app-ic oc"><Glyph name="layers" size={14} /></span>
							<span class="hb"><span class="meta">OpenShortcuts · now</span><b>Sleep Mode ran</b><span>WhatsApp call ended</span></span>
						{:else}
							<span class="app-ic wa"><Glyph name="phone" size={14} /></span>
							<span class="hb"><span class="meta">WhatsApp · Ongoing voice call</span><b>Mom</b><span class="num">{callLabel}</span></span>
							<span class="hang"><Glyph name="phoneOff" size={16} /></span>
						{/if}
					</div>
				{/if}
			</div>
		</div>
	</div>

	<div class="below">
		<button type="button" class="sim" onclick={phase === 'idle' ? simulate : reset}>
			<Glyph name={phase === 'idle' ? 'playFill' : 'undo'} size={16} />
			{phase === 'idle' ? 'Simulate a late call' : 'Reset'}
		</button>
		<p class="note-out" aria-live="polite">
			{#if phase === 'idle'}
				Tap an automation to open it. Go back with ‹ or by swiping from the left edge. Then simulate a call that runs past {autos[0].minutes} minutes.
			{:else if phase === 'calling'}
				It’s 23:47 and the call is still going…
			{:else if phase === 'ran'}
				Sleep Mode pressed Hang up on WhatsApp’s notification and dimmed the screen.
			{:else}
				The automation is off, so the call keeps going and your battery keeps draining.
			{/if}
		</p>
	</div>
</div>

<style>
	.demo {
		margin-top: 40px;
		display: grid;
		justify-items: center;
	}
	.device {
		max-width: 100%;
		padding: 10px;
		border-radius: 52px;
		background: #1a1a1d;
		box-shadow:
			inset 0 0 0 1.5px #3a3a40,
			0 40px 80px -30px rgb(0 0 0 / 0.45);
	}
	.viewport {
		position: relative;
		overflow: hidden;
		border-radius: 42px;
		background: var(--bg);
	}
	.screen {
		position: absolute;
		top: 0;
		left: 0;
		transform-origin: 0 0;
		overflow: hidden;
		background: var(--bg);
		color: var(--ink);
		font-family: var(--font);
		-webkit-user-select: none;
		user-select: none;
	}
	.screen::after {
		content: '';
		position: absolute;
		inset: 0;
		z-index: 20;
		background: black;
		opacity: 0;
		pointer-events: none;
		transition: opacity 900ms var(--ease-out);
	}
	.screen.dimmed::after {
		opacity: 0.45;
	}
	button {
		font: inherit;
		color: inherit;
		border: 0;
		background: none;
		padding: 0;
		cursor: pointer;
		touch-action: manipulation;
		-webkit-tap-highlight-color: transparent;
	}
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}

	.page {
		position: absolute;
		inset: 0;
		display: flex;
		background: var(--bg);
	}
	.scrim {
		position: absolute;
		inset: 0;
		background: rgb(0 0 0 / 0.14);
		pointer-events: none;
	}
	.detail-page {
		z-index: 2;
	}
	.detail-page.moving {
		box-shadow: -18px 0 18px -18px rgb(0 0 0 / 0.16);
	}
	.edge {
		position: absolute;
		inset: 0 auto 0 0;
		width: 24px;
		touch-action: none;
		cursor: grab;
	}

	.main {
		position: relative;
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: none;
	}
	.scroll::-webkit-scrollbar {
		display: none;
	}

	.head {
		padding: 18px 0;
	}
	.head.short {
		padding: 10px 250px 12px 0;
	}
	.head h3 {
		font-size: 30px;
		line-height: 34px;
		font-weight: 700;
		letter-spacing: -0.02em;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.head.short h3 {
		font-size: 28px;
		line-height: 32px;
	}
	.head p {
		margin-top: 2px;
		font-size: 13px;
		line-height: 18px;
		color: var(--ink2);
	}

	.card {
		background: var(--card);
		border-radius: 26px;
		box-shadow: none;
	}
	.tile {
		display: inline-grid;
		place-items: center;
		flex-shrink: 0;
		width: var(--s);
		height: var(--s);
		border-radius: calc(var(--s) * 0.27);
		background: var(--t);
		color: white;
	}

	.autos {
		display: grid;
		gap: 12px;
	}
	.auto {
		position: relative;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 12px 14px 16px;
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.auto:has(.open:active) {
		scale: 0.97;
	}
	.open {
		position: absolute;
		inset: 0;
		border-radius: 26px;
	}
	.auto .tile,
	.txt {
		pointer-events: none;
	}
	.txt {
		flex: 1;
		min-width: 0;
		display: grid;
	}
	.hl {
		font-size: 15px;
		line-height: 20px;
		font-weight: 500;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
	.sub {
		font-size: 13px;
		line-height: 18px;
		color: var(--ink2);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.switch {
		position: relative;
		flex-shrink: 0;
		margin-left: 8px;
		cursor: pointer;
	}
	.switch input {
		position: absolute;
		inset: -6px;
		width: calc(100% + 12px);
		height: calc(100% + 12px);
		margin: 0;
		opacity: 0;
		cursor: pointer;
		z-index: 1;
	}
	.switch .track {
		display: block;
		width: 51px;
		height: 31px;
		border-radius: 16px;
		background: var(--fill);
		transition: background-color 200ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.switch .thumb {
		display: block;
		width: 27px;
		height: 27px;
		margin: 2px;
		border-radius: 14px;
		background: white;
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.25);
		transition:
			translate 320ms cubic-bezier(0.23, 1, 0.32, 1),
			width 200ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.switch input:checked + .track {
		background: var(--positive);
	}
	.switch input:checked + .track .thumb {
		translate: 20px 0;
	}
	.switch input:active + .track .thumb {
		width: 34px;
	}
	.switch input:checked:active + .track .thumb {
		translate: 13px 0;
	}
	.switch input:focus-visible + .track {
		outline: 3px solid var(--accent);
		outline-offset: 2px;
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
		gap: 12px;
	}
	.stile {
		position: relative;
		display: block;
		width: 100%;
		height: 108px;
		border-radius: 22px;
		background: var(--t);
		color: white;
		text-align: left;
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.stile:active {
		scale: 0.97;
	}
	.stile.running {
		animation: breathe 700ms ease-in-out infinite alternate;
	}
	.stile.done {
		animation: pop 420ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.g {
		position: absolute;
		top: 14px;
		left: 14px;
		display: grid;
	}
	.gi {
		grid-area: 1 / 1;
		display: grid;
		animation: fade 200ms ease;
	}
	.more {
		position: absolute;
		top: 14px;
		right: 14px;
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		border-radius: 50%;
		background: rgb(255 255 255 / 0.24);
	}
	.more :global(path) {
		stroke-width: 3;
	}
	.nm {
		position: absolute;
		left: 14px;
		right: 14px;
		bottom: 14px;
		font-size: 16px;
		line-height: 20px;
		font-weight: 500;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.tabs {
		position: absolute;
		left: 16px;
		display: flex;
		padding: 5px;
		border-radius: 30px;
		background: var(--raised);
		box-shadow: 0 12px 28px -10px rgb(0 0 0 / 0.18);
	}
	.tabs.short {
		left: auto;
		right: 4px;
	}
	.tabs button {
		position: relative;
		z-index: 1;
		width: var(--seg-w);
		height: var(--seg-h);
		font-size: 15px;
		line-height: 20px;
		font-weight: 500;
		color: var(--ink2);
		transition:
			color 200ms ease,
			scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.tabs button:active {
		scale: 0.96;
	}
	.tabs button[aria-selected='true'] {
		color: var(--ink);
	}
	.tabs .pill {
		position: absolute;
		top: 5px;
		left: 5px;
		width: var(--seg-w);
		height: var(--seg-h);
		border-radius: calc(var(--seg-h) / 2);
		background: var(--fill);
		transition: translate 420ms cubic-bezier(0.23, 1, 0.32, 1);
	}

	.rail {
		width: 76px;
		flex-shrink: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.home {
		display: grid;
		place-items: center;
		width: 40px;
		height: 30px;
		color: var(--ink);
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.home.accent {
		color: var(--accent);
	}
	.home:active,
	.clock:active {
		scale: 0.9;
	}
	.clock {
		display: grid;
		min-height: 30px;
		place-items: center;
		font-size: 18px;
		line-height: 22px;
		font-weight: 600;
		letter-spacing: -0.01em;
		font-variant-numeric: tabular-nums;
		white-space: nowrap;
		overflow: hidden;
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.clock span {
		animation: slide 260ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.clock .date {
		font-size: 14px;
		color: var(--accent);
	}
	.battery {
		position: relative;
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		margin-top: 4px;
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.battery:active {
		scale: 0.92;
	}
	.battery svg {
		position: absolute;
		rotate: -90deg;
	}
	.battery .track {
		fill: none;
		stroke: var(--fill);
		stroke-width: 3;
	}
	.battery .arc {
		fill: none;
		stroke-width: 3;
		stroke-linecap: round;
		transition: stroke-dashoffset 650ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.bolt {
		position: absolute;
		top: 7px;
		display: grid;
		color: var(--positive);
	}
	.pct {
		font-size: 11px;
		line-height: 1;
		font-variant-numeric: tabular-nums;
	}
	.pct.down {
		translate: 0 6px;
	}
	.grow {
		flex: 1;
	}
	.controls {
		display: flex;
		flex-direction: column;
		align-items: center;
	}
	.glass {
		display: grid;
		place-items: center;
		width: 52px;
		height: 52px;
		border-radius: 50%;
		background: var(--raised);
		box-shadow:
			inset 0 0 0 1px var(--hairline),
			0 4px 12px -4px rgb(0 0 0 / 0.18);
		color: var(--ink);
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	button.glass:active {
		scale: 0.94;
	}

	.dhead {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 22px 0 20px;
	}
	.kind {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.kt {
		font-size: 22px;
		line-height: 26px;
		font-weight: 600;
		letter-spacing: -0.02em;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.chev {
		display: grid;
		place-items: center;
		flex-shrink: 0;
		width: 24px;
		height: 24px;
		margin-left: -4px;
		border-radius: 50%;
		background: var(--fill);
		color: var(--ink2);
	}
	.sentence {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 6px 10px 6px 16px;
	}
	.flow {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 6px 4px;
		padding: 10px 0;
	}
	.w {
		font-size: 17px;
		line-height: 24px;
		letter-spacing: -0.01em;
	}
	.pill-t {
		display: inline-flex;
		align-items: center;
		gap: 5px;
		padding: 1px 8px;
		border-radius: 9px;
		background: var(--accent-wash);
		color: var(--accent);
		font-size: 17px;
		line-height: 24px;
		font-weight: 500;
		letter-spacing: -0.01em;
		white-space: nowrap;
		transition: scale 150ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	button.pill-t:active {
		scale: 0.95;
	}
	.pill-t.with {
		padding-left: 4px;
	}
	.go {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		margin: 8px;
		border-radius: 50%;
		background: var(--accent-wash);
		color: var(--accent);
	}
	.gap {
		height: 12px;
	}
	.link {
		display: grid;
		place-items: center;
		height: 14px;
	}
	.link span {
		width: 2px;
		height: 14px;
		background: color-mix(in srgb, var(--ink3) 45%, transparent);
	}
	.note {
		margin-top: 8px;
		padding: 0 6px;
		font-size: 13px;
		line-height: 18px;
		color: var(--ink2);
	}
	.quiet {
		display: grid;
		place-items: center;
		min-height: 50px;
		margin-top: 28px;
		border-radius: 25px;
		background: var(--fill);
		color: var(--danger);
		font-size: 17px;
		line-height: 20px;
		font-weight: 500;
	}
	.seg {
		position: relative;
		display: flex;
		height: 36px;
		padding: 2px;
		border-radius: 10px;
		background: var(--fill);
	}
	.seg-pill {
		position: absolute;
		top: 2px;
		left: 2px;
		width: calc((100% - 4px) / 5);
		height: 32px;
		border-radius: 8px;
		background: var(--raised);
	}
	.seg-o {
		position: relative;
		flex: 1;
		display: grid;
		place-items: center;
		font-size: 13px;
		font-weight: 500;
		color: var(--ink2);
	}
	.seg-o.on {
		color: var(--ink);
	}

	.heads {
		position: absolute;
		left: 50%;
		z-index: 30;
		display: flex;
		align-items: center;
		gap: 12px;
		width: min(420px, calc(100% - 32px));
		padding: 12px 14px;
		border-radius: 24px;
		background: var(--raised);
		box-shadow: 0 10px 30px -8px rgb(0 0 0 / 0.35);
		translate: -50% 0;
		animation: drop 420ms cubic-bezier(0.23, 1, 0.32, 1);
	}
	.app-ic {
		display: grid;
		place-items: center;
		flex-shrink: 0;
		width: 26px;
		height: 26px;
		border-radius: 50%;
		color: white;
	}
	.app-ic.wa {
		background: #25d366;
	}
	.app-ic.oc {
		background: #3478f6;
	}
	.hb {
		flex: 1;
		min-width: 0;
		display: grid;
		font-size: 14px;
		line-height: 19px;
		color: var(--ink2);
	}
	.hb b {
		color: var(--ink);
		font-weight: 600;
	}
	.meta {
		font-size: 12px;
	}
	.num {
		font-variant-numeric: tabular-nums;
	}
	.hang {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border-radius: 50%;
		background: var(--tint-red);
		color: white;
	}

	.below {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: center;
		gap: 12px 18px;
		margin-top: 22px;
	}
	.sim {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		min-height: 44px;
		padding: 0 20px;
		border-radius: 999px;
		background: var(--ink);
		color: var(--bg);
		font-size: 0.9375rem;
		font-weight: 600;
		transition: scale 160ms var(--ease-out);
	}
	.sim:active {
		scale: 0.97;
	}
	.note-out {
		max-width: 34em;
		font-size: 0.9375rem;
		color: var(--ink2);
	}

	@keyframes breathe {
		to {
			opacity: 0.82;
		}
	}
	@keyframes pop {
		40% {
			scale: 1.04;
		}
	}
	@keyframes fade {
		from {
			opacity: 0;
		}
	}
	@keyframes slide {
		from {
			opacity: 0;
			translate: 0 60%;
		}
	}
	@keyframes drop {
		from {
			opacity: 0;
			translate: -50% -12px;
		}
	}

	@media (prefers-reduced-motion: reduce) {
		.stile.running,
		.stile.done,
		.clock span,
		.heads {
			animation: fade 200ms ease;
		}
		.switch .thumb,
		.tabs .pill,
		.battery .arc {
			transition-duration: 0ms;
		}
	}
</style>
