"""Docker/MySQL regression tests; use a unique disposable database, never business data.

Run: .venv-local/Scripts/python.exe tools/test_database_encoding.py
"""
import re
import subprocess
import unittest
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / 'database/migrations/20260918_text_encoding.sql'


def mysql(statement, charset='utf8mb4'):
    result = subprocess.run(
        ['docker', 'exec', '-i', 'water-mysql', 'sh', '-c',
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --raw '
         f'--skip-column-names --default-character-set={charset}'],
        input=statement.encode('utf-8'), stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, check=True)
    return result.stdout.decode('utf-8').strip()


class DatabaseEncodingTest(unittest.TestCase):
    def setUp(self):
        self.db = 'ccb_encoding_test_' + uuid.uuid4().hex
        mysql(f'CREATE DATABASE `{self.db}` CHARACTER SET utf8mb4;')
        self.addCleanup(self.remove_database)

    def remove_database(self):
        assert re.fullmatch(r'ccb_encoding_test_[0-9a-f]{32}', self.db)
        mysql(f'DROP DATABASE `{self.db}`;')

    def execute(self, statement, charset='utf8mb4'):
        return mysql(f'USE `{self.db}`;\n' + statement, charset)

    def test_fresh_init_overrides_legacy_client_charset(self):
        source = (ROOT / 'database/init.sql').read_text(encoding='utf-8-sig')
        source = source.replace('water_meter_db', self.db)
        mysql(source, 'latin1')
        names = self.execute('SELECT real_name FROM sys_user ORDER BY id;')
        self.assertEqual(names.splitlines(), ['张三', '李四', '王五'])
        self.assertEqual(self.execute('SELECT area_name FROM area WHERE id=1;'), '示范区A')
        self.assertEqual(self.execute('SELECT install_address FROM water_meter WHERE id=1;'), '示范区A街道1号')
        self.assertEqual(self.execute("SELECT description FROM sys_config WHERE config_key='sewage.rate';"), '污水处理费率')
        seed = (ROOT / 'database/seed_ops.sql').read_text(encoding='utf-8-sig').replace('water_meter_db', self.db)
        mysql(seed, 'latin1')
        self.assertEqual(self.execute("SELECT real_name FROM sys_user WHERE username='admin';"), '运营官')

    def test_repair_is_exact_and_repeatable(self):
        # Force the historical import bug even after init.sql is fixed.
        source = (ROOT / 'database/init.sql').read_text(encoding='utf-8-sig').replace('water_meter_db', self.db)
        source = re.sub(r'SET\s+NAMES\s+utf8mb4\s*;', 'SET NAMES latin1;', source, flags=re.I)
        mysql(source, 'latin1')
        self.assertNotEqual(self.execute('SELECT real_name FROM sys_user WHERE id=1;'), '张三')
        # Valid Chinese and foreign-language data must survive unchanged.
        self.execute("UPDATE sys_user SET real_name='李四（已更名）', balance=12.34 WHERE id=2;"
                     "UPDATE sys_user SET real_name='Renée' WHERE id=3;")
        before = self.execute('SELECT id,username,password,balance,update_time FROM sys_user ORDER BY id;')
        migration = MIGRATION.read_text(encoding='utf-8')
        for _ in range(2):
            self.execute(migration, 'latin1')
            self.assertEqual(self.execute('SELECT real_name FROM sys_user ORDER BY id;').splitlines(),
                             ['张三', '李四（已更名）', 'Renée'])
            self.assertEqual(self.execute('SELECT address FROM sys_user WHERE id=1;'), '示范区A街道1号')
            self.assertEqual(self.execute('SELECT install_address FROM water_meter WHERE id=3;'), '示范区A街道3号')
            self.assertEqual(self.execute('SELECT area_name FROM area WHERE id=2;'), '街道1')
            self.assertEqual(self.execute("SELECT description FROM sys_config WHERE config_key='sewage.rate';"), '污水处理费率')
            self.assertEqual(self.execute('SELECT id,username,password,balance,update_time FROM sys_user ORDER BY id;'), before)


if __name__ == '__main__':
    unittest.main(verbosity=2)
