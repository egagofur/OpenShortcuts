export const REPO = 'https://github.com/egagofur/OpenShortcuts';
export const APK = `${REPO}/releases/latest/download/OpenShortcuts.apk`;
export const RELEASES = `${REPO}/releases`;
export const ISSUES = `${REPO}/issues`;
export const SITE = 'https://openshortcuts.egagofur.studio/';

export const TITLE = 'OpenShortcuts: the Shortcuts app Android was missing';
export const DESCRIPTION =
	'Free, open-source Android automation inspired by iOS Shortcuts. Build routines from simple sentences: time, sunset, location, Bluetooth or WhatsApp. No root.';

export const FAQS = [
	{
		q: 'Does it need root?',
		a: 'No. Everything uses standard Android permissions and WhatsApp’s own notification buttons.'
	},
	{
		q: 'Does it work with the screen off?',
		a: 'Yes. Ending a WhatsApp call and replying to a chat use the buttons on WhatsApp’s notification, so they work while the phone is locked. Settings like Do Not Disturb and brightness work too.'
	},
	{
		q: 'Why does it ask for so many permissions?',
		a: 'It doesn’t ask for them all at once. Each permission is asked for only when one of your shortcuts needs it, with a short explanation of what it does and doesn’t do.'
	},
	{
		q: 'Does it work on Xiaomi, Oppo, Vivo and Samsung phones?',
		a: 'Yes, with one setting. These phones stop apps that run in the background. Turn on Auto launch (or Autostart) for OpenShortcuts; the app shows you where.'
	},
	{
		q: 'What does a workspace send, and where?',
		a: 'Only requests to the API you added, signed with a token you paste on the phone. The token is kept on the device, left out of backups and never written to exported files. The Demo workspace sends nothing at all.'
	},
	{
		q: 'Is it on Google Play?',
		a: 'Not yet. Download the APK from GitHub Releases, or build it yourself from the source.'
	}
] as const;

export type Tint = 'blue' | 'green' | 'indigo' | 'orange' | 'red' | 'gray' | 'teal' | 'yellow' | 'pink' | 'purple';
