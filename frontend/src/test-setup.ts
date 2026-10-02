declare const process: { env: Record<string, string | undefined> };

process.env['TZ'] = 'America/New_York';
